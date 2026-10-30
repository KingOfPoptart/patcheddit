package app.morphe.extension.syncforreddit;

import org.json.JSONArray;
import org.json.JSONObject;

import app.morphe.extension.shared.Logger;

import xa.d;

/**
 * Reddit now returns 403 for /api/info?url=, which "Other discussions" used.
 * Load /duplicates/{id} instead (the endpoint reddit.com itself uses for this).
 */
@SuppressWarnings("unused")
public class OtherDiscussionsExtension {
    private static final String MARKER = "morphedupes-";
    private static final String INFO_PREFIX = "https://oauth.reddit.com/api/info?url=" + MARKER;

    public static String getPostId(d post) {
        try {
            String id = post.U();
            if (id != null && id.startsWith("t3_")) id = id.substring(3);
            return id;
        } catch (Exception e) {
            return null;
        }
    }

    /** Swap the URL-based key ("seen###<url>") for one carrying the post ID. */
    public static String getOtherDiscussionsKey(String key, String postId) {
        if (postId == null || postId.isEmpty() || key == null) return key;
        int sep = key.indexOf("###");
        if (sep < 0) return key;
        return key.substring(0, sep + 3) + MARKER + postId;
    }

    public static String fixListingUrl(String url) {
        if (url == null || !url.startsWith(INFO_PREFIX)) return url;
        String rest = url.substring(INFO_PREFIX.length());
        int amp = rest.indexOf('&');
        String id = amp < 0 ? rest : rest.substring(0, amp);
        String query = amp < 0 ? "" : rest.substring(amp);
        String fixed = "https://oauth.reddit.com/duplicates/" + id + ".json?limit=100" + query;
        Logger.printInfo(() -> "Other discussions: loading " + fixed);
        return fixed;
    }

    /** /duplicates returns [originalPostListing, duplicatesListing]; keep the duplicates. */
    public static String unwrapDuplicatesResponse(String body) {
        try {
            if (body != null && body.startsWith("[")) {
                JSONArray array = new JSONArray(body);
                if (array.length() >= 2) {
                    JSONObject duplicates = array.getJSONObject(1);
                    Logger.printInfo(() -> "Other discussions: " + duplicates.optJSONObject("data").optJSONArray("children").length() + " results");
                    return duplicates.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return body;
    }
}
