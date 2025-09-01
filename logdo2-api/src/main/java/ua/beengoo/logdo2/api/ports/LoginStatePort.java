package ua.beengoo.logdo2.api.ports;

import java.time.Instant;
import java.time.Duration;
import java.util.Collection;
import java.util.UUID;

public interface LoginStatePort {
    // ===== First login (Java): STATE TOKENS =====
    String createOAuthState(UUID uuid, String ip, String name, boolean bedrock);
    OAuthState consumeOAuthState(String token);
    boolean hasOAuthState(String token);

    // ===== Bedrock: one time codes (/login <code>) =====
    String createOneTimeCode(UUID uuid, String ip, String name);
    PendingCode consumeOneTimeCode(String code);

    // ===== IP confirm =====
    void markPendingIpConfirm(UUID uuid, String newIp, long discordId);
    boolean isPendingIpConfirm(UUID uuid);
    PendingIp consumePendingIpConfirm(UUID uuid);
    /** For timeout checks. */
    Collection<PendingIp> listPendingIpConfirms();

    // ===== First login pending (block an action) =====
    void markPendingLogin(UUID uuid, String ip, boolean bedrock);
    boolean isPendingLogin(UUID uuid);
    void clearPendingLogin(UUID uuid);
    /** For timeout checks. */
    Collection<PendingLogin> listPendingLogins();

    // ===== Bedrock code resend support =====
    /** Record that a Bedrock login code was shown to the player now. */
    void recordBedrockCodeShown(UUID uuid, String code);
    /** Record the moment a Bedrock player left while pending login. */
    void recordBedrockLeave(UUID uuid);
    /**
     * Return last shown code if the player left recently (within maxAge) and a code is known.
     */
    java.util.Optional<String> recentBedrockCodeAfterLeave(UUID uuid, Duration maxAge);

    // ===== Дані структур =====
    record PendingIp(UUID uuid, String newIp, long discordId, Instant at) {}
    record OAuthState(UUID uuid, String ip, String name, boolean bedrock, Instant at) {}
    record PendingCode(String code, UUID uuid, String ip, String name, Instant at) {}
    record PendingLogin(UUID uuid, String ip, boolean bedrock, Instant at) {}
}
