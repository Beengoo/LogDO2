package ua.beengoo.logdo2.api;

import net.dv8tion.jda.api.JDA;

import java.util.Optional;
import java.util.UUID;

/**
 * Convenience facade with common read-only queries for other plugins.
 */
public interface LogDO2Api {
    boolean isLinked(UUID uuid);
    Optional<Long> discordId(UUID uuid);
    Optional<String> lastConfirmedIp(UUID uuid);
    boolean isActionAllowed(UUID uuid, String currentIp);
    JDA getDiscordBot();
}

