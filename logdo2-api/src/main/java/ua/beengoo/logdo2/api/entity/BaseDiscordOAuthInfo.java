package ua.beengoo.logdo2.api.entity;

public interface BaseDiscordOAuthInfo {

    Long getDiscordId();
    String getAccessToken();
    String getRefreshToken();
    String getTokenType();
    String getScopes();

    Long getExpiredAt();
    Long getUpdatedAt();

    boolean refreshIfPossible();
}
