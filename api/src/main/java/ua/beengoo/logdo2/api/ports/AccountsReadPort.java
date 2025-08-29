package ua.beengoo.logdo2.api.ports;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only view for account linking info exposed to other plugins.
 */
public interface AccountsReadPort {
    boolean isLinked(UUID profileUuid);
    Optional<Long> findDiscordForProfile(UUID profileUuid);
    Set<UUID> findProfilesForDiscord(long discordId);
}

