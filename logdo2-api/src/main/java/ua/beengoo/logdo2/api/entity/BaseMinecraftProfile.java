package ua.beengoo.logdo2.api.entity;

import java.util.UUID;

public interface BaseMinecraftProfile {
    /**
     * @return The player's UUID
     */
    UUID getUuid();

    /**
     * @return The player's name
     */
    String getName();

    /**
     * @return The player's platform (JAVA or BEDROCK)
     */
    String getPlatform();

    /**
     * @return Last validated player IP address
     */
    String getLastValidIp();
}
