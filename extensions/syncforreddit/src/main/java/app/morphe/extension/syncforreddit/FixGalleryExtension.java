package app.morphe.extension.syncforreddit;

import android.content.ContentValues;

import org.json.JSONArray;
import org.json.JSONObject;

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

    /**
     * Sync's parser gives crossposted galleries no preview image (sync_type 6 = gallery without preview).
     * Fill it in from the parent post the same way Sync does for regular galleries (sync_type 10).
     */
    public static void addCrosspostGalleryPreview(ContentValues values, JSONObject post) {
        try {
            JSONArray parents = post.optJSONArray("crosspost_parent_list");
            if (parents == null || parents.length() == 0) return;
            JSONObject parent = parents.getJSONObject(0);
            JSONObject galleryData = parent.optJSONObject("gallery_data");
            JSONObject mediaMetadata = parent.optJSONObject("media_metadata");
            if (galleryData == null || mediaMetadata == null) return;

            String firstId = galleryData.getJSONArray("items").getJSONObject(0).getString("media_id");
            JSONObject media = mediaMetadata.getJSONObject(firstId);
            JSONArray previews = media.optJSONArray("p");
            JSONObject image = (previews != null && previews.length() > 0)
                    ? previews.getJSONObject(previews.length() - 1)
                    : media.getJSONObject("s");

            values.put("preview_url", image.getString("u").replace("&amp;", "&"));
            values.put("preview_width", image.getInt("x"));
            values.put("preview_height", image.getInt("y"));
            values.put("sync_type", 10);

            // List view uses the separate "thumbnail" column, filled from the crosspost's own "thumbnail"
            // field, which for crossposts often isn't a URL. Fall back to the parent's, then to a small
            // gallery preview (Reddit's list thumbnails are ~140px, so the 216px one is plenty).
            if (values.getAsString("thumbnail") == null) {
                String thumbnail = parent.optString("thumbnail");
                if (!thumbnail.startsWith("http") && previews != null && previews.length() > 0) {
                    thumbnail = previews.getJSONObject(Math.min(1, previews.length() - 1)).getString("u");
                }
                if (thumbnail.startsWith("http")) {
                    values.put("thumbnail", thumbnail.replace("&amp;", "&"));
                }
            }
        } catch (Exception ignored) {
            // Leave Sync's original placeholder in place.
        }
    }
}
