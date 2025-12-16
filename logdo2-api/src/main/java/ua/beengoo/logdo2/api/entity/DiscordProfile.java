package ua.beengoo.logdo2.api.entity;

public class DiscordProfile implements BaseDiscordProfile{

    private final DiscordOAuthInfo discordOAuthInfo;

    public DiscordProfile(DiscordOAuthInfo discordOAuthInfo){
        this.discordOAuthInfo = discordOAuthInfo;
    }

    @Override
    public DiscordOAuthInfo getOAuthInfo() {
        return discordOAuthInfo;
    }

    @Override
    public boolean isUserAuthorized() {
        return false;
    }
}
