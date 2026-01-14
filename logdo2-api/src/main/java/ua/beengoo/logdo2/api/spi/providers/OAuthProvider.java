package ua.beengoo.logdo2.api.spi.providers;

import java.time.Instant;

public interface OAuthProvider {
    String buildAuthUrl(String state, String redirectUri);

    TokenSet exchangeCode(String code, String redirectUri);

    DiscordUser fetchUser(String accessToken);

    record TokenSet(String accessToken, String refreshToken, Instant expiresAt,
                    String tokenType, String scope) {}

    record DiscordUser(long id, String username, String globalName,
                       String email, String avatar) {}
}
