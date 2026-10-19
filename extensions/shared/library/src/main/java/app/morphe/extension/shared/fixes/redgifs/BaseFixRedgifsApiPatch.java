package app.morphe.extension.shared.fixes.redgifs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Locale;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.requests.PatchedditInterceptor;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;


public abstract class BaseFixRedgifsApiPatch extends PatchedditInterceptor {
    protected static BaseFixRedgifsApiPatch INSTANCE;
	private static final Pattern GIF_LOOKUP_ID_PATTERN = Pattern.compile("^[a-z0-9]+$");
    public abstract String getDefaultUserAgent();

    public boolean isPatchIncluded() {
        // Overridden by patch.
        return false;
    }

    @NonNull
    @Override
    public Response doIntercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        if (!request.url().host().equals("api.redgifs.com")) {
            return chain.proceed(request);
        }

        final String path = request.url().encodedPath();
        Logger.printInfo(() -> "Redgifs: intercepted " + request.method() + " " + path);
		final String gifId = extractGifLookupId(path);

        String userAgent = getDefaultUserAgent();
        boolean forceTokenRefresh = false;

        if (request.header("Authorization") != null) {
            Response response;
            try {
                response = chain.proceed(request.newBuilder().header("User-Agent", userAgent).build());
			} catch (IOException ex) {
				if (gifId != null) {
					Logger.printInfo(() -> "Redgifs: request failed (existing Authorization); synthesizing offline response for " + gifId);
					return buildLocalJsonResponse(request, buildOfflineGifJson(gifId));
				}
				Logger.printException(() -> "Redgifs: request failed (existing Authorization) for " + path, ex);
				throw ex;
			}
            if (response.isSuccessful()) {
                Logger.printInfo(() -> "Redgifs: request succeeded (existing Authorization) for " + path
                        + ", code=" + response.code());
                return response;
            }
            Logger.printInfo(() -> "Redgifs: request failed (existing Authorization) for " + path
                    + ", code=" + response.code() + "; refreshing token");
            // It's possible that the user agent is being overwritten later down in the interceptor
            // chain, so make sure we grab the new user agent from the request headers.
            String responseUserAgent = response.request().header("User-Agent");
            if (responseUserAgent != null && !responseUserAgent.isEmpty()) {
                userAgent = responseUserAgent;
            }
            response.close();

            // Redgifs tokens are tied to the IP address that requested them. A cached token can
            // therefore become invalid before its normal expiry when the device changes networks.
            // Do not retry the failed request with the same cached token.
            forceTokenRefresh = true;
        }
        final String finalUserAgent = userAgent;

        try {
            // Emulate response for the old client IP lookup endpoint, which Redgifs has removed.
            // It was only ever used to populate the legacy "user-addr" query parameter, which
            // current Redgifs endpoints accept but ignore, so the actual value doesn't matter.
            if (path.equals("/info")) {
                Logger.printInfo(() -> "Redgifs: emulating /info response locally");
                return buildLocalJsonResponse(request, RedgifsTokenManager.getEmulatedIpResponseBody());
            }

            RedgifsTokenManager.RedgifsToken token;
            try {
                token = RedgifsTokenManager.refreshToken(userAgent, forceTokenRefresh);
			} catch (IOException ex) {
				if (gifId != null) {
					Logger.printInfo(() -> "Redgifs: token fetch failed; synthesizing offline response for " + gifId);
					return buildLocalJsonResponse(request, buildOfflineGifJson(gifId));
				}
				Logger.printException(() -> "Redgifs: failed to obtain temporary token for user agent \""
						+ finalUserAgent + "\"", ex);
				throw ex;
			}

            // Emulate response for old OAuth endpoint
            if (path.equals("/v2/oauth/client")) {
                Logger.printInfo(() -> "Redgifs: emulating /v2/oauth/client response locally");
                String responseBody = RedgifsTokenManager.getEmulatedOAuthResponseBody(token);
                return buildLocalJsonResponse(request, responseBody);
            }

            Request modifiedRequest = request.newBuilder()
                    .header("Authorization", "Bearer " + token.getAccessToken())
                    .header("User-Agent", userAgent)
                    .build();
            Response response;
            try {
                response = chain.proceed(modifiedRequest);
			} catch (IOException ex) {
				if (gifId != null) {
					Logger.printInfo(() -> "Redgifs: request failed; synthesizing offline response for " + gifId);
					return buildLocalJsonResponse(request, buildOfflineGifJson(gifId));
				}
				Logger.printException(() -> "Redgifs: request failed for " + path, ex);
				throw ex;
			}
            Logger.printInfo(() -> "Redgifs: request for " + path + " returned code=" + response.code());
			if (gifId != null && !response.isSuccessful() && isRecoverableApiFailure(response.code())) {
				Logger.printInfo(() -> "Redgifs: API blocked for " + gifId + " (code=" + response.code()
						+ "); synthesizing offline response");
				response.close();
				return buildLocalJsonResponse(request, buildOfflineGifJson(gifId));
			}
			return response;
        } catch (JSONException ex) {
            Logger.printException(() -> "Could not parse Redgifs response", ex);
            throw new IOException(ex);
        }
    }

    private static Response buildLocalJsonResponse(Request request, String jsonBody) {
        return new Response.Builder()
                .message("OK")
                .code(HttpURLConnection.HTTP_OK)
                .protocol(Protocol.HTTP_1_1)
                .request(request)
                .header("Content-Type", "application/json")
                .body(ResponseBody.create(jsonBody, MediaType.get("application/json")))
                .build();
    }
	
	@Nullable
	private static String extractGifLookupId(String path) {
		for (String prefix : new String[]{"/v2/gifs/", "/api/v2/gifs/"}) {
			if (path.startsWith(prefix)) {
				String id = path.substring(prefix.length()).toLowerCase(Locale.ROOT);
				if (id.isEmpty() || id.equals("search")) return null;
				return GIF_LOOKUP_ID_PATTERN.matcher(id).matches() ? id : null;
			}
		}
		return null;
	}

	private static String buildOfflineGifJson(String id) {
		try {
			String mp4    = "https://media.redgifs.com/" + id + ".mp4";
			String mobile = "https://media.redgifs.com/" + id + "-mobile.mp4";
			String poster = "https://media.redgifs.com/" + id + "-poster.jpg";
			String thumb  = "https://media.redgifs.com/" + id + "-mobile-poster.jpg";
			JSONObject urls = new JSONObject()
					.put("hd", mp4).put("sd", mobile).put("mp4", mp4)
					.put("mp4Mobile", mobile).put("mobile", mobile).put("gif", mp4)
					.put("poster", poster).put("thumbnail", thumb).put("vthumbnail", thumb);
			JSONObject files = new JSONObject()
					.put("hd", new JSONObject().put("contentUrl", mp4).put("width", 1280).put("height", 720))
					.put("sd", new JSONObject().put("contentUrl", mobile).put("width", 640).put("height", 360))
					.put("poster", new JSONObject().put("contentUrl", poster));
			JSONObject gif = new JSONObject()
					.put("id", id).put("gfyId", id)
					.put("userName", "redgifs").put("title", id).put("description", "")
					.put("width", 1280).put("height", 720).put("duration", 10.0)
					.put("hasAudio", true).put("createDate", System.currentTimeMillis() / 1000)
					.put("views", 0).put("likes", 0).put("tags", new JSONArray())
					.put("url", "https://www.redgifs.com/watch/" + id)
					.put("urls", urls).put("files", files);
			return new JSONObject().put("gif", gif).toString();
		} catch (JSONException ex) {
			return "{\"gif\":{\"id\":\"" + id + "\",\"urls\":{\"hd\":\"https://media.redgifs.com/"
					+ id + ".mp4\",\"sd\":\"https://media.redgifs.com/" + id + "-mobile.mp4\"}}}";
		}
	}

	private static boolean isRecoverableApiFailure(int code) {
		return code == 401 || code == 403 || code == 407 || code == 429 || (code >= 500 && code <= 599);
	}
}
