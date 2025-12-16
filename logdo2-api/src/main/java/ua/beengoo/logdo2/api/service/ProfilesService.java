package ua.beengoo.logdo2.api.service;

import org.bukkit.OfflinePlayer;
import ua.beengoo.logdo2.api.entity.LogDO2Profile;

import java.util.UUID;

/**
 * High-level profile service exposing read-only profile queries.
 *
 * Implementations should be thin adapters that compose lower-level ports
 * (for example `AccountsRepo`, `ProfileRepo`) and provide a stable, modular surface
 * for other plugins or remote protocol layers to depend on.
 */
public interface ProfilesService {

    /**
     * Returns profile for given Discord id or null when none found.
     */
    LogDO2Profile getProfile(Long discordId);

    /**
     * Returns profile for given Minecraft UUID or null when none found.
     */
    LogDO2Profile getProfile(UUID minecraftUUID);

    /**
     * Returns profile for the provided OfflinePlayer instance.
     */
    LogDO2Profile getProfile(OfflinePlayer player);
}
