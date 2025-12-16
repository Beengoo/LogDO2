package ua.beengoo.logdo2.api.service;

import ua.beengoo.logdo2.api.DiscordAccount;
import ua.beengoo.logdo2.api.MinecraftProfile;
import ua.beengoo.logdo2.api.entity.DiscordProfile;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only account linking operations.
 */
public interface AccountsService {
    boolean isLinked(UUID profileUuid);
    Optional<DiscordProfile> findDiscordForProfile(UUID profileUuid);
    Set<UUID> findProfilesForDiscord(long discordId);
    DiscordAccount getDiscordAccount(long discordId);
    List<MinecraftProfile> getUsersByDiscord(long discordId);
}
