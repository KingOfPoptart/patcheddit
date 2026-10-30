package app.morphe.extension.syncforreddit;

import org.json.JSONArray;

/**
 * Reddit now returns 403 for /api/info.json?id=t3_..., which Sync used to load galleries.
 * The /comments/{id}.json endpoint returns the same post data and still works.
 */
@SuppressWarnings("unused")
public class FixGalleryExtension {

    public static String buildGalleryUrl(String url) {
        return "https://oauth.reddit.com/comments/" + getPostId(url) + ".json?limit=1&depth=1";
    }

    /** Same logic as Sync's own LinkHelper.getCommentsId(), plus query/fragment stripping. */
    private static String getPostId(String url) {
        String s = url.replace("?is_gallery=true", "");
        int cut = s.indexOf('?');
        if (cut >= 0) s = s.substring(0, cut);
        cut = s.indexOf('#');
        if (cut >= 0) s = s.substring(0, cut);
        if (s.contains("/comments/")) {
            return s.split("/comments/")[1].split("/")[0];
        }
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s.substring(s.lastIndexOf('/') + 1);
    }

    /** /comments returns [postListing, commentListing]; Sync's parser expects just postListing. */
    public static String unwrapGalleryResponse(String body) {
        try {
            if (body != null && body.trim().startsWith("[")) {
                return new JSONArray(body).getJSONObject(0).toString();
            }
        } catch (Exception ignored) {
        }
        return body;
    }
}
