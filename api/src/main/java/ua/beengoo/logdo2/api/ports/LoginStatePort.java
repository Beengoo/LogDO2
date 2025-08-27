package ua.beengoo.logdo2.api.ports;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface LoginStatePort {
    // ===== First login (Java): STATE токен =====
    String createOAuthState(UUID uuid, String ip, String name, boolean bedrock);
    OAuthState consumeOAuthState(String token);
    boolean hasOAuthState(String token);

    // ===== Bedrock: одноразові коди (/login <code>) =====
    String createOneTimeCode(UUID uuid, String ip, String name);
    PendingCode consumeOneTimeCode(String code);

    // ===== IP confirm =====
    void markPendingIpConfirm(UUID uuid, String newIp, long discordId);
    boolean isPendingIpConfirm(UUID uuid);
    PendingIp consumePendingIpConfirm(UUID uuid);
    /** Для перевірки таймаутів. */
    Collection<PendingIp> listPendingIpConfirms();

    // ===== First login pending (блокування дій) =====
    void markPendingLogin(UUID uuid, String ip, boolean bedrock);
    boolean isPendingLogin(UUID uuid);
    void clearPendingLogin(UUID uuid);
    /** Для перевірки таймаутів. */
    Collection<PendingLogin> listPendingLogins();

    // ===== Дані структур =====
    record PendingIp(UUID uuid, String newIp, long discordId, Instant at) {}
    record OAuthState(UUID uuid, String ip, String name, boolean bedrock, Instant at) {}
    record PendingCode(String code, UUID uuid, String ip, String name, Instant at) {}
    record PendingLogin(UUID uuid, String ip, boolean bedrock, Instant at) {}
}
