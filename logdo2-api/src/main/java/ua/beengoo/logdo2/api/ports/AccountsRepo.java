package ua.beengoo.logdo2.api.ports;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AccountsRepo {
    /** Create or update link as active=1 (fully linked). */
    void link(long discordId, UUID profileUuid);

    /** Reserve a profile for a Discord ID without activating (active=0). */
    void reserve(long discordId, UUID profileUuid);

    /** Activate the reserved link for this pair (sets active=1 and deactivates others for the profile). */
    void activate(long discordId, UUID profileUuid);
    Optional<Long> linkedAt(UUID profileUUID);
    boolean isLinked(UUID profileUuid);
    Optional<Long> findDiscordForProfile(UUID profileUuid);
    /** Returns any discord id reserved/linked for this profile, regardless of active flag. */
    Optional<Long> findAnyDiscordForProfile(UUID profileUuid);
    Set<UUID> findProfilesForDiscord(long discordId);
    /** Count profiles linked to discord by platform (platform values: "JAVA"/"BEDROCK"). */
    int countByDiscordAndPlatform(long discordId, String platform, boolean includeReserved);
    void unlinkByProfile(java.util.UUID profileUuid);
    void unlinkByDiscord(long discordId);

    void unlinkByDiscordAndProfile(long discordId, UUID profileUuid);
}
