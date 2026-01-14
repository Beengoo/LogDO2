package ua.beengoo.logdo2.api.entity;

public interface BaseDiscordProfile {
    BaseDiscordOAuthInfo getOAuthInfo();


    /**
     * @return true if authenticated by user itself, not manually added
     */
    boolean isUserAuthenticated();
}
