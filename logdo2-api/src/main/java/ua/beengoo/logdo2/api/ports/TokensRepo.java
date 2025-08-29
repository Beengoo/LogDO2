package ua.beengoo.logdo2.api.ports;

import java.time.Instant;
import java.util.Optional;

public interface TokensRepo {
    void save(long discordId,
              String accessToken, String refreshToken, Instant expiresAt,
              String tokenType, String scope);

    record TokenView(String accessToken, String refreshToken, Instant expiresAt,
                     String tokenType, String scope) {}

    Optional<TokenView> find(long discordId);
}
