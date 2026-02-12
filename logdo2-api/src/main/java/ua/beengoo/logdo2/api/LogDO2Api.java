package ua.beengoo.logdo2.api;

import ua.beengoo.logdo2.api.entity.LogDO2Profile;
import ua.beengoo.logdo2.api.entity.WebServerInfo;

import java.util.UUID;

/**
 * Public API for LogDO2 - simple queries for external plugins.
 */
public interface LogDO2Api {
    /**
     * Is API implementation made for Minecraft Proxy software
     * @return true, if implementation is made for proxy software, not actual server
     */
    boolean isProxySoftware();

    /**
     * Get a unified profile by Discord ID
     * @param discordId The Discord user ID
     * @return The profile, or null if not found
     */
    LogDO2Profile getProfile(Long discordId);

    /**
     * Get a unified profile by Minecraft UUID
     * @param minecraftUUID The Minecraft player UUID
     * @return The profile, or null if not found
     */
    LogDO2Profile getProfile(UUID minecraftUUID);

    /**
     * Get the Discord bot instance
     * @return The Discord bot instance
     */
    Object getDiscordBot();

    /**
     * Get Discord API provider
     * @return The Discord API provider name
     */
    String getDiscordAPiProvider();

    /**
     * Gets web server implementation info
     * @return WebServerInfo object
     */
    WebServerInfo getWebServerInfo();

    /**
     * Get the target Discord guild ID
     * @return The guild ID
     */
    String getTargetGuildId();

    /**
     * Check if a player is allowed to perform actions (linked and IP confirmed)
     * @param uuid The Minecraft player UUID
     * @param currentIp The player's current IP address
     * @return true if allowed, false otherwise
     */
    boolean isActionAllowed(UUID uuid, String currentIp);
}

