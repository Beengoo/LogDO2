package ua.beengoo.logdo2.core.service;

import ua.beengoo.logdo2.api.ports.LoginStatePort;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Тримає тимчасові стани: OAuth state і Bedrock коди + pending логін/IP. */
public class LoginStateService implements LoginStatePort {
    private static final Duration OAUTH_STATE_TTL = Duration.ofMinutes(10);
    private static final Duration CODE_TTL        = Duration.ofMinutes(10);

    private final Map<String, OAuthState> oauthStates = new ConcurrentHashMap<>();
    private final Map<String, PendingCode> codes      = new ConcurrentHashMap<>();
    private final Map<UUID, PendingLogin> pendingLogin = new ConcurrentHashMap<>();
    private final Map<UUID, PendingIp> pendingIp = new ConcurrentHashMap<>();
    private final SecureRandom rnd = new SecureRandom();

    @Override
    public String createOAuthState(UUID uuid, String ip, String name, boolean bedrock) {
        String token = generateStateToken();
        oauthStates.put(token, new OAuthState(uuid, ip, name, bedrock, Instant.now()));
        return token;
    }

    @Override
    public OAuthState consumeOAuthState(String token) {
        pruneStates();
        var st = oauthStates.remove(token);
        if (st == null) throw new IllegalStateException("Invalid or expired state");
        return st;
    }

    @Override
    public boolean hasOAuthState(String token) {
        pruneStates();
        return oauthStates.containsKey(token);
    }

    @Override
    public String createOneTimeCode(UUID uuid, String ip, String name) {
        String code;
        do {
            code = generateShortCode();
        } while (codes.containsKey(code));
        codes.put(code, new PendingCode(code, uuid, ip, name, Instant.now()));
        return code;
    }

    @Override
    public PendingCode consumeOneTimeCode(String code) {
        pruneCodes();
        return codes.remove(code);
    }

    @Override
    public void markPendingIpConfirm(UUID uuid, String newIp, long discordId) {
        pendingIp.put(uuid, new PendingIp(uuid, newIp, discordId, Instant.now()));
    }

    @Override
    public boolean isPendingIpConfirm(UUID uuid) {
        return pendingIp.containsKey(uuid);
    }

    @Override
    public PendingIp consumePendingIpConfirm(UUID uuid) {
        return pendingIp.remove(uuid);
    }

    @Override
    public Collection<PendingIp> listPendingIpConfirms() {
        return Collections.unmodifiableCollection(pendingIp.values());
    }

    @Override
    public void markPendingLogin(UUID uuid, String ip, boolean bedrock) {
        pendingLogin.put(uuid, new PendingLogin(uuid, ip, bedrock, Instant.now()));
    }

    @Override
    public boolean isPendingLogin(UUID uuid) {
        return pendingLogin.containsKey(uuid);
    }

    @Override
    public void clearPendingLogin(UUID uuid) {
        pendingLogin.remove(uuid);
    }

    @Override
    public Collection<PendingLogin> listPendingLogins() {
        return Collections.unmodifiableCollection(pendingLogin.values());
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
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // без схожих символів
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        return sb.toString();
    }
}
