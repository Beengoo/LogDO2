package ua.beengoo.logdo2.api.ports;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AccountsRepo {
    void link(long discordId, UUID profileUuid);
    boolean isLinked(UUID profileUuid);
    Optional<Long> findDiscordForProfile(UUID profileUuid);
    Set<UUID> findProfilesForDiscord(long discordId);
    void unlinkByProfile(java.util.UUID profileUuid);
    void unlinkByDiscord(long discordId);

    void unlinkByDiscordAndProfile(long discordId, UUID profileUuid);
}
