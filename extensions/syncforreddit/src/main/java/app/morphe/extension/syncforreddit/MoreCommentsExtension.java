package app.morphe.extension.syncforreddit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import app.morphe.extension.shared.Logger;

import xa.d;

/**
 * Reddit now blocks /api/morechildren ("blocked by network security", 403), which Sync's
 * "load more comments" used. Instead, load the parent comment's subtree (or, for top-level
 * stubs, each hidden comment by ID) from /comments/{post}/_/{id}.json, and rebuild the old
 * morechildren response ({"json":{"data":{"things":[...]}}}) so Sync's parser is unchanged.
 */
@SuppressWarnings("unused")
public class MoreCommentsExtension {
    private static final String PARENT_PARAM = "&morphe_parent=";
    /** Most hidden comments fetched one by one per tap. The rest get a new "load more" stub. */
    private static final int MAX_SINGLE_FETCHES = 20;
    private static final int THREADS = 6;

    private static volatile String pendingParent;

    /** Called just before Sync builds the request for this "load more" stub. */
    public static void setPendingParent(d stub) {
        try {
            pendingParent = stub.t0();
        } catch (Exception e) {
            pendingParent = null;
        }
    }

    /** Replace the morechildren URL with a GET of the comments tree that holds the hidden comments. */
    public static String buildUrl(String moreChildrenUrl, String linkId, String children, String sort) {
        String parent = pendingParent;
        pendingParent = null;
        try {
            int api = moreChildrenUrl.indexOf("api/morechildren");
            if (api < 0 || linkId == null) return moreChildrenUrl;
            String base = moreChildrenUrl.substring(0, api);
            String[] ids = splitIds(children);
            if (parent == null) parent = "t3_" + linkId;

            String url;
            if (parent.startsWith("t1_")) {
                url = base + "comments/" + linkId + "/_/" + parent.substring(3) + ".json?limit=500";
            } else if (ids.length > 0) {
                url = base + "comments/" + linkId + "/_/" + ids[0] + ".json?limit=100";
            } else {
                url = base + "comments/" + linkId + ".json?limit=1";
            }
            url += "&always_show_media=1&profile_img=true" + sortParam(sort)
                    + PARENT_PARAM + URLEncoder.encode(parent, "UTF-8");
            String finalUrl = url;
            Logger.printInfo(() -> "More comments: loading " + finalUrl);
            return url;
        } catch (Exception e) {
            Logger.printException(() -> "More comments: failed to build URL", e);
            return moreChildrenUrl;
        }
    }

    /** Turn the comments tree response into a morechildren response. */
    public static String transform(String body, Map<String, String> params, Map<String, String> headers, String requestUrl) {
        try {
            if (body == null || !body.startsWith("[") || params == null || requestUrl == null) return body;
            int p = requestUrl.indexOf(PARENT_PARAM);
            if (p < 0) return body;
            String parent = URLDecoder.decode(cut(requestUrl.substring(p + PARENT_PARAM.length())), "UTF-8");
            String linkId = params.get("link_id");
            if (linkId != null && linkId.startsWith("t3_")) linkId = linkId.substring(3);
            String[] wanted = splitIds(params.get("children"));
            Set<String> wantedSet = new HashSet<>();
            for (String id : wanted) wantedSet.add(id);

            // Each hidden comment found -> it plus its replies, flattened parent-first.
            Map<String, List<JSONObject>> found = new HashMap<>();
            collect(listingOf(body), wantedSet, found);

            List<String> missing = new ArrayList<>();
            for (String id : wanted) if (!found.containsKey(id)) missing.add(id);

            int fetchCount = Math.min(missing.size(), MAX_SINGLE_FETCHES);
            if (fetchCount > 0) {
                String base = requestUrl.substring(0, requestUrl.indexOf("comments/"));
                String sort = sortParam(params.get("sort"));
                ExecutorService pool = Executors.newFixedThreadPool(Math.min(THREADS, fetchCount));
                try {
                    Map<String, Future<String>> futures = new LinkedHashMap<>();
                    for (int i = 0; i < fetchCount; i++) {
                        String id = missing.get(i);
                        String url = base + "comments/" + linkId + "/_/" + id
                                + ".json?limit=100&always_show_media=1&profile_img=true" + sort;
                        futures.put(id, pool.submit(() -> get(url, headers)));
                    }
                    for (Future<String> f : futures.values()) {
                        try {
                            collect(listingOf(f.get(30, TimeUnit.SECONDS)), wantedSet, found);
                        } catch (Exception e) {
                            Logger.printInfo(() -> "More comments: single fetch failed: " + e);
                        }
                    }
                } finally {
                    pool.shutdownNow();
                }
            }

            JSONArray things = new JSONArray();
            List<String> rest = new ArrayList<>();
            for (int i = 0; i < wanted.length; i++) {
                List<JSONObject> list = found.get(wanted[i]);
                if (list != null) {
                    for (JSONObject thing : list) things.put(thing);
                } else if (missing.indexOf(wanted[i]) >= fetchCount) {
                    rest.add(wanted[i]); // not tried yet; deleted/removed ones are dropped
                }
            }
            if (!rest.isEmpty()) things.put(moreStub(parent, rest));

            int loaded = found.size(), remaining = rest.size();
            Logger.printInfo(() -> "More comments: " + wanted.length + " requested, " + loaded
                    + " loaded, " + remaining + " left in a new stub");

            JSONObject data = new JSONObject().put("things", things);
            return new JSONObject().put("json", new JSONObject().put("errors", new JSONArray()).put("data", data)).toString();
        } catch (Exception e) {
            Logger.printException(() -> "More comments: failed to transform response", e);
            return body;
        }
    }

    private static JSONArray listingOf(String body) throws Exception {
        JSONArray array = new JSONArray(body);
        return array.getJSONObject(1).getJSONObject("data").getJSONArray("children");
    }

    /** Walk a comments tree; each wanted comment found is stored with its flattened subtree. */
    private static void collect(JSONArray children, Set<String> wanted, Map<String, List<JSONObject>> found) throws Exception {
        for (int i = 0; i < children.length(); i++) {
            JSONObject thing = children.getJSONObject(i);
            JSONObject data = thing.optJSONObject("data");
            if (data == null) continue;
            String id = data.optString("id");
            if ("t1".equals(thing.optString("kind")) && wanted.contains(id)) {
                if (!found.containsKey(id)) {
                    List<JSONObject> out = new ArrayList<>();
                    flatten(thing, out);
                    found.put(id, out);
                }
            } else if ("t1".equals(thing.optString("kind"))) {
                JSONObject replies = data.optJSONObject("replies");
                if (replies != null) collect(replies.getJSONObject("data").getJSONArray("children"), wanted, found);
            }
        }
    }

    /** Add a comment and all its descendants (including nested "more" stubs), parent first. */
    private static void flatten(JSONObject thing, List<JSONObject> out) throws Exception {
        out.add(thing);
        if (!"t1".equals(thing.optString("kind"))) return;
        JSONObject data = thing.getJSONObject("data");
        JSONObject replies = data.optJSONObject("replies");
        data.put("replies", "");
        if (replies == null) return;
        JSONArray children = replies.getJSONObject("data").getJSONArray("children");
        for (int i = 0; i < children.length(); i++) flatten(children.getJSONObject(i), out);
    }

    private static JSONObject moreStub(String parent, List<String> ids) throws Exception {
        JSONArray children = new JSONArray();
        for (String id : ids) children.put(id);
        JSONObject data = new JSONObject()
                .put("count", ids.size())
                .put("name", "t1_" + ids.get(0))
                .put("id", ids.get(0))
                .put("parent_id", parent)
                .put("children", children);
        return new JSONObject().put("kind", "more").put("data", data);
    }

    private static String get(String url, Map<String, String> headers) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) conn.setRequestProperty(h.getKey(), h.getValue());
            int code = conn.getResponseCode();
            if (code != 200) throw new IllegalStateException("HTTP " + code + " for " + url);
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) bytes.write(buf, 0, n);
                return bytes.toString("UTF-8");
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String[] splitIds(String csv) {
        if (csv == null || csv.trim().isEmpty()) return new String[0];
        List<String> ids = new ArrayList<>();
        for (String s : csv.split(",")) {
            s = s.trim();
            if (s.startsWith("t1_")) s = s.substring(3);
            if (!s.isEmpty()) ids.add(s);
        }
        return ids.toArray(new String[0]);
    }

    private static String sortParam(String sort) {
        if (sort == null || sort.isEmpty() || "suggested".equalsIgnoreCase(sort)) return "";
        return "&sort=" + sort.toLowerCase();
    }

    private static String cut(String s) {
        int amp = s.indexOf('&');
        return amp < 0 ? s : s.substring(0, amp);
    }
}
