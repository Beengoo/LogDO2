package ua.beengoo.logdo2.core.service;

import ua.beengoo.logdo2.api.events.LoginPhase;
import ua.beengoo.logdo2.api.spi.providers.PropertiesProvider;
import ua.beengoo.logdo2.api.spi.providers.Properties;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LoginStateService {
    private static final Duration OAUTH_STATE_TTL = Duration.ofMinutes(10);
    private static final Duration CODE_TTL        = Duration.ofMinutes(10);

    private final PropertiesProvider propertiesProvider;

    private final Map<String, OAuthState> oauthStates = new ConcurrentHashMap<>();
    private final Map<String, PendingCode> codes      = new ConcurrentHashMap<>();
    private final Map<UUID, PendingLogin> pendingLogin = new ConcurrentHashMap<>();
    private final Map<UUID, PendingIp> pendingIp = new ConcurrentHashMap<>();
    private final Map<UUID, BedrockShown> bedrockShown = new ConcurrentHashMap<>();
    private final Set<UUID> limitBypass = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final SecureRandom rnd = new SecureRandom();

    public LoginStateService(PropertiesProvider propertiesProvider) {
        this.propertiesProvider = propertiesProvider;
    }

    public String createOAuthState(UUID uuid, String ip, String name, boolean bedrock) {
        String token = generateStateToken();
        oauthStates.put(token, new OAuthState(uuid, ip, name, bedrock, Instant.now()));
        return token;
    }

    public OAuthState consumeOAuthState(String token) {
        pruneStates();
        var st = oauthStates.remove(token);
        if (st == null) throw new IllegalStateException("Invalid or expired state");
        return st;
    }

    public boolean hasOAuthState(String token) {
        pruneStates();
        return oauthStates.containsKey(token);
    }

    public LoginPhase getLoginPhase(UUID uuid) {
        if (pendingIp.containsKey(uuid)) return LoginPhase.IP_CONFIRM;
        if (pendingLogin.containsKey(uuid)) return LoginPhase.LOGIN;
        return null;
    }

    public String createOneTimeCode(UUID uuid, String ip, String name) {
        String code;
        do {
            code = generateShortCode();
        } while (codes.containsKey(code));
        codes.put(code, new PendingCode(code, uuid, ip, name, Instant.now()));
        bedrockShown.put(uuid, new BedrockShown(code, Instant.now(), null));
        return code;
    }

    public PendingCode consumeOneTimeCode(String code) {
        pruneCodes();
        PendingCode pc = codes.get(code);
        if (pc == null) return null;
        Properties props = propertiesProvider.getSnapshot();

        // If the owner left and the reuse window passed, invalidate this code
        BedrockShown info = bedrockShown.get(pc.uuid());
        if (info != null && info.leftAt() != null) {
            Instant cutoff = Instant.now().minus(Duration.ofSeconds(props.bedrockCodeTimeAfterLeave));
            if (cutoff.isAfter(info.leftAt())) {
                // Expired after leave; remove and refuse consumption
                codes.remove(code);
                return null;
            }
        }

        // Valid — remove and return
        return codes.remove(code);
    }

    public void markPendingIpConfirm(UUID uuid, String newIp, long discordId) {
        pendingIp.put(uuid, new PendingIp(uuid, newIp, discordId, Instant.now()));
    }

    public boolean isPendingIpConfirm(UUID uuid) {
        return pendingIp.containsKey(uuid);
    }

    public PendingIp consumePendingIpConfirm(UUID uuid) {
        return pendingIp.remove(uuid);
    }

    public Collection<PendingIp> listPendingIpConfirms() {
        return Collections.unmodifiableCollection(pendingIp.values());
    }

    public void markPendingLogin(UUID uuid, String ip, String token, boolean bedrock) {
        pendingLogin.put(uuid, new PendingLogin(uuid, ip, bedrock, token, Instant.now()));
    }

    public boolean isPendingLogin(UUID uuid) {
        return pendingLogin.containsKey(uuid);
    }

    public void clearPendingLogin(UUID uuid) {
        pendingLogin.remove(uuid);
        bedrockShown.remove(uuid);
    }

    public Collection<PendingLogin> listPendingLogins() {
        return Collections.unmodifiableCollection(pendingLogin.values());
    }

    public void recordBedrockCodeShown(UUID uuid, String code) {
        bedrockShown.compute(uuid, (u, prev) -> new BedrockShown(code, Instant.now(), prev == null ? null : prev.leftAt()));
    }

    public void recordBedrockLeave(UUID uuid) {
        bedrockShown.compute(uuid, (u, prev) -> new BedrockShown(prev == null ? null : prev.code(), prev == null ? null : prev.shownAt(), Instant.now()));
    }

    public Optional<String> recentBedrockCodeAfterLeave(UUID uuid, Duration maxAge) {
        var info = bedrockShown.get(uuid);
        if (info == null || info.code() == null || info.leftAt() == null) return Optional.empty();
        if (Instant.now().minus(maxAge).isAfter(info.leftAt())) return Optional.empty();
        return Optional.of(info.code());
    }

    // ===== admin: limit bypass =====
    public void grantLimitBypass(UUID uuid) {
        if (uuid != null) limitBypass.add(uuid);
    }

    public boolean hasLimitBypass(UUID uuid) {
        if (uuid == null) return false;
        return limitBypass.contains(uuid);
    }

    public boolean consumeLimitBypass(UUID uuid) {
        if (uuid == null) return false;
        return limitBypass.remove(uuid);
    }

    // ===== helpers =====
    private void pruneStates() {
        Instant cutoff = Instant.now().minus(OAUTH_STATE_TTL);
        oauthStates.entrySet().removeIf(e -> e.getValue().at().isBefore(cutoff));
    }

    private void pruneCodes() {
        Instant cutoff = Instant.now().minus(CODE_TTL);
        codes.entrySet().removeIf(e -> e.getValue().at().isBefore(cutoff));
    }

    private String generateStateToken() {
        byte[] b = new byte[16];
        rnd.nextBytes(b);
        StringBuilder sb = new StringBuilder(32);
        for (byte x : b) sb.append(String.format(Locale.ROOT, "%02x", x));
        return sb.toString();
    }

    private String generateShortCode() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        return sb.toString();
    }

    // Record types for state management
    public record OAuthState(UUID uuid, String ip, String name, boolean bedrock, Instant at) {}
    public record PendingCode(String code, UUID uuid, String ip, String name, Instant at) {}
    public record PendingLogin(UUID uuid, String ip, boolean bedrock, String token, Instant at) {}
    public record PendingIp(UUID uuid, String newIp, long discordId, Instant at) {}
    private record BedrockShown(String code, Instant shownAt, Instant leftAt) {}
}
