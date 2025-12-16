package ua.beengoo.logdo2.api.entity;

public interface BaseDiscordProfile {
    BaseDiscordOAuthInfo getOAuthInfo();


    /**
     * @return true if authorized by user itself, not manually added
     */
    boolean isUserAuthorized();
}
