package ua.beengoo.logdo2.api.ports;

public interface DiscordUserRepo {
    void upsertUser(long discordId,
                    String username, String globalName,
                    String email, String avatarHash);

    /** Позначаємо, що користувач видав applications.commands у цій інсталяції. */
    void setCommandsInstalled(long discordId, boolean installed);
}
