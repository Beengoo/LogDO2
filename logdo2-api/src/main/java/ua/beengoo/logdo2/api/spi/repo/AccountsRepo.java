package ua.beengoo.logdo2.api.spi.repo;

import java.util.Map;
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

    /**
     * Check if a link is marked as primary
     * @param discordId Discord user ID
     * @param profileUuid Minecraft profile UUID
     * @return true if this link is marked as primary
     */
    boolean isPrimaryLink(long discordId, UUID profileUuid);

    /**
     * Get the primary Minecraft profile for a Discord user
     * @param discordId Discord user ID
     * @return UUID of primary profile, or empty if none marked
     */
    Optional<UUID> findPrimaryProfileForDiscord(long discordId);

    /**
     * Set a link as primary (and unset others for same discord_id)
     * @param discordId Discord user ID
     * @param profileUuid Minecraft profile UUID to mark as primary
     */
    void setPrimaryLink(long discordId, UUID profileUuid);

    /**
     * Get link details including is_primary flag
     * @param discordId Discord user ID
     * @return Map of UUID to is_primary boolean for all active links
     */
    Map<UUID, Boolean> findLinksWithPrimaryFlag(long discordId);

    void unlinkByProfile(java.util.UUID profileUuid);
    void unlinkByDiscord(long discordId);

    void unlinkByDiscordAndProfile(long discordId, UUID profileUuid);
}
