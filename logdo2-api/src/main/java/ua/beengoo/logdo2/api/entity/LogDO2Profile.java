package ua.beengoo.logdo2.api.entity;

import java.util.List;

public class LogDO2Profile implements BaseLogDO2Profile{

    private final String profileId;
    private final DiscordProfile discordProfile;
    private LogDO2ProfileStatus profileStatus;
    private final List<LinkInfo> linkInfoList;

    public LogDO2Profile(String profileId, DiscordProfile discordProfile,
                         LogDO2ProfileStatus profileStatus, List<LinkInfo> linkInfoList){
        this.profileId = profileId;
        this.discordProfile = discordProfile;
        this.profileStatus = profileStatus;
        this.linkInfoList = linkInfoList;
    }

    @Override
    public DiscordProfile getDiscordProfile() {
        return discordProfile;
    }

    @Override
    public String getProfileId() {
        return profileId;
    }

    @Override
    public LogDO2ProfileStatus getProfileStatus() {
        return profileStatus;
    }

    @Override
    public long getCreatedAt() {
        return discordProfile.getOAuthInfo().getCreatedAt();
    }

    @Override
    public List<LinkInfo> getLinkInfo() {
        return linkInfoList;
    }

    @Override
    public void setProfileStatus(LogDO2ProfileStatus status) {
        profileStatus = status;
    }
}
