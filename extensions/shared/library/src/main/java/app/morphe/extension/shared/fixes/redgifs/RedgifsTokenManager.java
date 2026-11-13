package app.morphe.extension.shared.fixes.redgifs;

import androidx.annotation.GuardedBy;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import app.morphe.extension.shared.Logger;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;


/**
 * Manages Redgifs token lifecycle.
 */
public class RedgifsTokenManager {
    public static class RedgifsToken {
        // Expire after 23 hours to provide some breathing room
        private static final long EXPIRY_SECONDS = 23 * 60 * 60;

        private final String accessToken;
        private final long refreshTimeInSeconds;

        public RedgifsToken(String accessToken, long refreshTime) {
            this.accessToken = accessToken;
            this.refreshTimeInSeconds = refreshTime;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public long getExpiryTimeInSeconds() {
            return refreshTimeInSeconds + EXPIRY_SECONDS;
        }

        public boolean isValid() {
            if (accessToken == null) return false;
            return getExpiryTimeInSeconds() >= System.currentTimeMillis() / 1000;
        }
    }
    public static final String REDGIFS_API_HOST = "https://api.redgifs.com";
    private static final String GET_TEMPORARY_TOKEN = REDGIFS_API_HOST + "/v2/auth/temporary";
    @GuardedBy("itself")
    private static final Map<String, RedgifsToken> tokenMap = new HashMap<>();

    /**
     * Fetch a temporary token through the caller's own OkHttp chain rather than a separate
     * HttpURLConnection.
     *
     * Redgifs binds a token to the IP address and user agent that requested it, and answers
     * 401 if the following API call arrives from a different one. A separate HTTP stack does
     * not share the app client's DNS policy, connection pool or downstream interceptors, so on
     * dual-stack networks (notably VPNs with distinct IPv4/IPv6 exit addresses) the token could
     * be issued to one address and then used from another. Going through the same chain keeps
     * both requests on the same route and with the same on-wire headers.
     */
    private static String getToken(Interceptor.Chain chain, String userAgent) throws IOException, JSONException {
        Request tokenRequest = new Request.Builder()
                .url(GET_TEMPORARY_TOKEN)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .build();

        try (Response response = chain.proceed(tokenRequest)) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("Redgifs temporary token request failed, code=" + response.code());
            }
            JSONObject responseObject = new JSONObject(body.string());
            Logger.printInfo(() -> "Redgifs: temporary token issued to addr=" + responseObject.optString("addr")
                    + ", agent=\"" + responseObject.optString("agent") + "\"");
            return responseObject.getString("token");
        }
    }

    public static RedgifsToken refreshToken(Interceptor.Chain chain, String userAgent, boolean forceRefresh)
            throws IOException, JSONException {
        synchronized(tokenMap) {
            // Reference: https://github.com/JeffreyCA/Apollo-ImprovedCustomApi/pull/67
            RedgifsToken token = tokenMap.get(userAgent);
            if (!forceRefresh && token != null && token.isValid()) {
                return token;
            }

            // Copy user agent from original request if present because Redgifs verifies
            // that the user agent in subsequent requests matches the one in the OAuth token.
            String accessToken = getToken(chain, userAgent);
            long refreshTime = System.currentTimeMillis() / 1000;
            token = new RedgifsToken(accessToken, refreshTime);
            tokenMap.put(userAgent, token);
            return token;
        }
    }

    public static String getEmulatedOAuthResponseBody(RedgifsToken token) throws JSONException {
        // Reference: https://github.com/JeffreyCA/Apollo-ImprovedCustomApi/pull/67
        JSONObject responseObject = new JSONObject();
        responseObject.put("access_token", token.accessToken);
        responseObject.put("expiry_time", token.getExpiryTimeInSeconds() - (System.currentTimeMillis() / 1000));
        responseObject.put("scope", "read");
        responseObject.put("token_type", "Bearer");
        return responseObject.toString();
    }

    public static String getEmulatedIpResponseBody() throws JSONException {
        // The real endpoint is gone, and current Redgifs endpoints accept but ignore the
        // "user-addr" value that's populated from this response, so any placeholder works.
        JSONObject responseObject = new JSONObject();
        responseObject.put("remote-addr", "0.0.0.0");
        return responseObject.toString();
    }
}
