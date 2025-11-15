package ua.beengoo.logdo2.api.entity;


import java.util.List;

/**
 * Generic LogDO2 Profile interface
 * */
public interface LogDO2Profile {


    /**
     * Returns profile id which contains hashed discord id and login timestamp
     * @return LogDO2 profile id if exists
     */
    String getProfileId();

    /**
     * @return LogDO2 profile status if profile exists
     */
    LogDO2ProfileStatus getProfileStatus();

    List<LinkInfo> getLinkInfo();

}
