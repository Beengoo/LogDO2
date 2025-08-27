package ua.beengoo.logdo2.plugin.adapters.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import ua.beengoo.logdo2.api.ports.OAuthPort;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

public class DiscordOAuthAdapter implements OAuthPort {
    private static final MediaType FORM = MediaType.parse("application/x-www-form-urlencoded");
    private final Logger log;
    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper om = new ObjectMapper();
    private final String clientId;
    private final String clientSecret;
    private final String scopesCfg; // наприклад: "identify email applications.commands"

    public DiscordOAuthAdapter(Logger log, String clientId, String clientSecret, String scopes) {
        this.log = log;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scopesCfg = scopes == null ? "identify email applications.commands" : scopes;
    }

    @Override
    public String buildAuthUrl(String state, String redirectUri) {
        String base = "https://discord.com/oauth2/authorize";
        String scopes = normalizeScopes(scopesCfg);
        return base + "?client_id=" + enc(clientId)
                + "&integration_type=1"
                + "&redirect_uri=" + enc(redirectUri)
                + "&response_type=code"
                + "&scope=" + enc(scopes)
                + "&state=" + enc(state)
                + "&prompt=consent";
    }

    @Override
    public TokenSet exchangeCode(String code, String redirectUri) {
        // Token endpoint ЗАЛИШАЄТЬСЯ з /api
        String body = "client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(redirectUri);

        Request req = new Request.Builder()
                .url("https://discord.com/api/oauth2/token")
                .post(RequestBody.create(body, FORM))
                .build();

        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new IOException("token exchange failed: " + res.code());
            JsonNode json = om.readTree(res.body().byteStream());
            String access   = json.get("access_token").asText();
            String refresh  = json.get("refresh_token").asText();
            long expiresIn  = json.get("expires_in").asLong(3600);
            String tokenTyp = json.has("token_type") ? json.get("token_type").asText() : "Bearer";
            String scope    = json.has("scope") ? json.get("scope").asText() : normalizeScopes(scopesCfg);
            return new TokenSet(access, refresh, Instant.now().plusSeconds(expiresIn), tokenTyp, scope);
        } catch (IOException e) {
            log.warning("OAuth exchange error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public DiscordUser fetchUser(String accessToken) {
        Request req = new Request.Builder()
                .url("https://discord.com/api/users/@me")
                .header("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new IOException("fetch user failed: " + res.code());
            JsonNode j = om.readTree(res.body().byteStream());
            long id = j.get("id").asLong();
            String username = j.hasNonNull("username") ? j.get("username").asText() : null;
            String global   = j.hasNonNull("global_name") ? j.get("global_name").asText() : null;
            String email    = j.hasNonNull("email") ? j.get("email").asText() : null; // потребує scope "email"
            String avatar   = j.hasNonNull("avatar") ? j.get("avatar").asText() : null;
            return new DiscordUser(id, username, global, email, avatar);
        } catch (IOException e) {
            log.warning("OAuth fetchUser error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static String normalizeScopes(String scopesStr) {
        if (scopesStr == null || scopesStr.isBlank()) return "identify email applications.commands";
        Set<String> set = new LinkedHashSet<>();
        for (String raw : scopesStr.split("[\\s,]+")) {
            if (raw.isBlank()) continue;
            String s = raw.toLowerCase(Locale.ROOT).trim();
            if ("identity".equals(s)) s = "identify";
            if ("application.commands".equals(s)) s = "applications.commands";
            set.add(s);
        }
        if (set.isEmpty()) set.addAll(Arrays.asList("identify", "email", "applications.commands"));
        return String.join(" ", set);
    }
}
