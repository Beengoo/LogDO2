package ua.beengoo.logdo2.api.spi.repo;

import java.util.Optional;

public interface DiscordUserRepo {
    void upsertUser(long discordId,
                    String username, String globalName,
                    String email, String avatarHash);

    Optional<String> findEmailByDiscordId(long discordId);

    /** Позначаємо, що користувач видав applications.commands у цій інсталяції. */
    void setCommandsInstalled(long discordId, boolean installed);

    /**
     * Get created_at timestamp for a Discord user
     * @param discordId Discord user ID
     * @return created_at epoch seconds, or empty if user not found
     */
    Optional<Long> findCreatedAtByDiscordId(long discordId);

    /**
     * Get profile_id for a Discord user
     * @param discordId Discord user ID
     * @return profile_id, or empty if user not found
     */
    Optional<String> findProfileIdByDiscordId(long discordId);
}
