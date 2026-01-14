package ua.beengoo.logdo2.api;

import ua.beengoo.logdo2.api.entity.LogDO2Profile;

import java.util.UUID;

/**
 * Public API for LogDO2 - simple read-only queries for external plugins.
 * This API provides access to unified profile entities and basic helper methods.
 */
public interface LogDO2Api {

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
     * Get the Discord bot instance for integrations
     * @return The Discord bot instance (JDA)
     */
    Object getDiscordBot();

    /**
     * Get the target Discord guild ID
     * @return The guild ID
     */
    String getTargetGuildId();

    /**
     * Check if a Minecraft profile is linked to a Discord account
     * @param uuid The Minecraft player UUID
     * @return true if linked, false otherwise
     */
    boolean isLinked(UUID uuid);

    /**
     * Check if a player is allowed to perform actions (linked and IP confirmed)
     * @param uuid The Minecraft player UUID
     * @param currentIp The player's current IP address
     * @return true if allowed, false otherwise
     */
    boolean isActionAllowed(UUID uuid, String currentIp);
}

