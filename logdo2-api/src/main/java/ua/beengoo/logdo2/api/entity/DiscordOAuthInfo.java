package ua.beengoo.logdo2.api.entity;

public class DiscordOAuthInfo implements BaseDiscordOAuthInfo {
    private final long discordId;
    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final String scopes;
    private final long expiredAt;
    private final long updatedAt;
    private final long createdAt;

    public DiscordOAuthInfo(long discordId, String accessToken, String refreshToken,
                           String tokenType, String scopes, long expiredAt, long updatedAt, long createdAt) {
        this.discordId = discordId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.scopes = scopes;
        this.expiredAt = expiredAt;
        this.updatedAt = updatedAt;
        this.createdAt = createdAt;
    }

    @Override
    public Long getDiscordId() {
        return discordId;
    }

    @Override
    public String getAccessToken() {
        return accessToken;
    }

    @Override
    public String getRefreshToken() {
        return refreshToken;
    }

    @Override
    public String getTokenType() {
        return tokenType;
    }

    @Override
    public String getScopes() {
        return scopes;
    }

    @Override
    public Long getExpiredAt() {
        return expiredAt;
    }

    @Override
    public Long getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public Long getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean refreshIfPossible() {
        // TODO: Implement token refresh logic if required, or force user to reauthenticate
        return false;
    }
}
