package ua.beengoo.logdo2.api.entity;

public class DiscordOAuthInfo implements BaseDiscordOAuthInfo{
    @Override
    public Long getDiscordId() {
        return 0L;
    }

    @Override
    public String getAccessToken() {
        return "";
    }

    @Override
    public String getRefreshToken() {
        return "";
    }

    @Override
    public String getTokenType() {
        return "";
    }

    @Override
    public String getScopes() {
        return "";
    }

    @Override
    public Long getExpiredAt() {
        return 0L;
    }

    @Override
    public Long getUpdatedAt() {
        return 0L;
    }

    @Override
    public boolean refreshIfPossible() {
        return false;
    }
}
