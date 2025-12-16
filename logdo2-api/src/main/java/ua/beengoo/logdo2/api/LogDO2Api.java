package ua.beengoo.logdo2.api;

import net.dv8tion.jda.api.JDA;
import org.bukkit.OfflinePlayer;
import ua.beengoo.logdo2.api.entity.LogDO2Profile;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Convenience facade with common read-only queries for other plugins.
 */
public interface LogDO2Api {

    LogDO2Profile getProfile(Long discordId);
    LogDO2Profile getProfile(UUID minecraftUUID);
    LogDO2Profile getProfile(OfflinePlayer player);

    JDA getDiscordBot();
    String getTargetGuildId();

    @Deprecated
    boolean isLinked(UUID uuid);
    @Deprecated
    Optional<Long> discordId(UUID uuid);
    @Deprecated
    Set<UUID> getProfiles(Long discordId);
    @Deprecated
    Optional<String> lastConfirmedIp(UUID uuid);
    @Deprecated
    boolean isActionAllowed(UUID uuid, String currentIp);
    @Deprecated
    DiscordAccount getDiscordAccount(long discordId);
    @Deprecated
    MinecraftProfile getMinecraftProfile(UUID uuid);
    @Deprecated
    SessionView getSessionForProfile(UUID uuid);
    @Deprecated
    List<MinecraftProfile> getUsersByDiscord(long discordId);
}

