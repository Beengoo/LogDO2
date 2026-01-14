package ua.beengoo.logdo2.api.entity;

public class DiscordProfile implements BaseDiscordProfile {
    private final DiscordOAuthInfo oauthInfo;

    public DiscordProfile(DiscordOAuthInfo oauthInfo) {
        this.oauthInfo = oauthInfo;
    }

    @Override
    public DiscordOAuthInfo getOAuthInfo() {
        return oauthInfo;
    }

    @Override
    public boolean isUserAuthenticated() {
        // TODO Check if OAuth2 tokens are valid then request authentication
        return oauthInfo != null &&
               oauthInfo.getAccessToken() != null &&
               !oauthInfo.getAccessToken().isEmpty();
    }
}
