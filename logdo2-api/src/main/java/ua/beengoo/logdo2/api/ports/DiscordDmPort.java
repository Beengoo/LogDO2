package ua.beengoo.logdo2.api.ports;

import java.util.UUID;

public interface DiscordDmPort {
    /** Перше прив’язування — інформуємо користувача. */
    void sendFirstLoginDm(long discordId, UUID mcUuid, String playerName, String publicUrl);

    /** Підтвердження IP — тепер з ім’ям гравця. */
    void sendIpConfirmDm(long discordId, UUID mcUuid, String playerName, String newIp);

    /** Для Bedrock: відправляємо лінк на завершення OAuth. */
    void sendFinalizeOAuthLink(long discordId, String url);
}
