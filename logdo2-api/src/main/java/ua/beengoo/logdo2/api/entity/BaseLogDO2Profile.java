package ua.beengoo.logdo2.api.entity;


import java.util.List;

/**
 * Generic LogDO2 Profile interface
 * */
public interface BaseLogDO2Profile {

    BaseDiscordProfile getDiscordProfile();
    /**
     * Returns profile id which contains hashed discord id and login timestamp
     * @return LogDO2 profile id if exists
     */
    String getProfileId();

    /**
     * @return LogDO2 profile status if profile exists
     */
    LogDO2ProfileStatus getProfileStatus();

    long getCreatedAt();
    /**
     * @return List of profile links
     */
    List<? extends BaseLinkInfo> getLinkInfo();

    /**
     * Sets profile status
     * */
    void setProfileStatus(LogDO2ProfileStatus status);
}
