package ua.beengoo.logdo2.core.service;

import org.junit.jupiter.api.Test;
import ua.beengoo.logdo2.api.ports.*;
import ua.beengoo.logdo2.api.provider.Properties;
import ua.beengoo.logdo2.api.provider.PropertiesProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class LoginServiceJoinTest {
    private static final MessagesPort MSG = new MessagesPort() {
        @Override public String raw(String path) { return path; }
        @Override public String mc(String path) { return path; }
        @Override public String mc(String path, Map<String, String> placeholders) { return path; }
    };

    private static final PropertiesProvider props = new PropertiesProvider() {
        @Override
        public ua.beengoo.logdo2.api.provider.Properties getSnapshot() {
            return new Properties(
                    60,
                    1, 1,
                    true, true, 0, 2, 4, 100,
                    true
            );
        }
    };

    @Test
    void bedrockJoinCreatesPendingLoginAndPlatform() {
        try (var ignored = BukkitStub.install()) {
            var state = new LoginStateService(props);
            var profiles = new TestProfileRepo();
            var accounts = new TestAccountsRepo();
            var service = createService(state, profiles, accounts, new NoopDm());

            UUID uuid = UUID.randomUUID();
            service.onPlayerJoin(uuid, "BedrockUser", "5.5.5.5", true);

            assertTrue(state.isPendingLogin(uuid));
            assertEquals("BedrockUser", profiles.names.get(uuid));
            assertEquals("BEDROCK", profiles.platforms.get(uuid));
            var pending = state.listPendingLogins().stream().filter(pl -> pl.uuid().equals(uuid)).findFirst().orElseThrow();
            assertTrue(pending.bedrock());
        }
    }

    @Test
    void ipChangeTriggersPendingConfirmationAndDm() {
        try (var ignored = BukkitStub.install()) {
            var state = new LoginStateService(props);
            var profiles = new TestProfileRepo();
            var dm = new RecordingDm();
            var accounts = new TestAccountsRepo();

            UUID uuid = UUID.randomUUID();
            long discordId = 123L;

            profiles.lastIp.put(uuid, "1.2.3.4");
            accounts.linked.add(uuid);
            accounts.active.put(uuid, discordId);
            accounts.any.put(uuid, discordId);

            var service = createService(state, profiles, accounts, dm);

            service.onPlayerJoin(uuid, "Player", "9.9.9.9", false);

            assertTrue(state.isPendingIpConfirm(uuid));
            var pending = state.listPendingIpConfirms().stream().filter(pi -> pi.uuid().equals(uuid)).findFirst().orElseThrow();
            assertEquals("9.9.9.9", pending.newIp());
            assertEquals(discordId, pending.discordId());

            assertEquals(discordId, dm.lastDiscordId);
            assertEquals(uuid, dm.lastProfile);
            assertEquals("Player", dm.lastName);
            assertEquals("9.9.9.9", dm.lastIp);
        }
    }

    @Test
    void oauthCallbackConsumesBypassAndActivatesAccount() {
        try (var ignored = BukkitStub.install()) {
            var state = new LoginStateService(props);
            var profiles = new TestProfileRepo();
            var dm = new RecordingDm();
            var accounts = new TestAccountsRepo();
            var tokens = new RecordingTokensRepo();
            var discordRepo = new RecordingDiscordUserRepo();
            var banRepo = new StubBanProgressRepo();
            var oauth = new RecordingOAuth();

            UUID uuid = UUID.randomUUID();
            long discordId = 999L;

            accounts.counts.put(key(discordId, "JAVA"), 1);
            accounts.reserve(discordId, uuid);
            accounts.any.put(uuid, discordId);

            var service = new LoginService(
                    oauth,
                    dm,
                    accounts,
                    profiles,
                    tokens,
                    state,
                    Logger.getLogger("test"),
                    "http://public",
                    "http://public/cb",
                    discordRepo,
                    null,
                    banRepo,
                    props,
                    MSG
            );

            String stateToken = service.createOAuthState(uuid, "4.4.4.4", "Player", false);
            state.grantLimitBypass(uuid);
            service.onOAuthCallback("code", stateToken);

            assertFalse(state.hasLimitBypass(uuid));
            assertFalse(state.isPendingLogin(uuid));
            assertEquals("JAVA", profiles.platforms.get(uuid));
            assertEquals("4.4.4.4", profiles.lastIp.get(uuid));

            assertEquals(discordId, accounts.lastActivatedDiscord);
            assertEquals(uuid, accounts.lastActivatedProfile);

            assertEquals(discordId, tokens.savedDiscordId.get());
            assertEquals("code-access", tokens.savedAccess.get());

            assertTrue(discordRepo.upserted.contains(discordId));
            assertEquals(Boolean.TRUE, discordRepo.commandsInstalled.get(discordId));

            assertEquals(discordId, dm.firstLoginDiscordId);
            assertEquals(uuid, dm.firstLoginProfile);
        }
    }

    private static String key(long discordId, String platform) {
        return discordId + ":" + platform.toUpperCase(Locale.ROOT);
    }

    private LoginService createService(LoginStateService state, TestProfileRepo profiles, TestAccountsRepo accounts, DiscordDmPort dm) {
        return new LoginService(
                new RecordingOAuth(),
                dm,
                accounts,
                profiles,
                new RecordingTokensRepo(),
                state,
                Logger.getLogger("test"),
                "http://public",
                "http://public/cb",
                null,
                null,
                new StubBanProgressRepo(),
                props,
                MSG
        );
    }

    private static class TestProfileRepo implements ProfileRepo {
        final Map<UUID, String> names = new HashMap<>();
        final Map<UUID, String> platforms = new HashMap<>();
        final Map<UUID, String> lastIp = new HashMap<>();

        @Override public Optional<String> findLastConfirmedIp(UUID profileUuid) { return Optional.ofNullable(lastIp.get(profileUuid)); }
        @Override public void updateLastConfirmedIp(UUID profileUuid, String ip) { lastIp.put(profileUuid, ip); }
        @Override public void upsertName(UUID profileUuid, String playerName) { names.put(profileUuid, playerName); }
        @Override public void updatePlatform(UUID profileUuid, String platform) { platforms.put(profileUuid, platform); }
        @Override public Optional<UUID> findUuidByName(String name) { return names.entrySet().stream().filter(e -> Objects.equals(e.getValue(), name)).map(Map.Entry::getKey).findFirst(); }
        @Override public Optional<String> findNameByUuid(UUID uuid) { return Optional.ofNullable(names.get(uuid)); }
        @Override public Optional<String> findPlatform(UUID uuid) { return Optional.ofNullable(platforms.get(uuid)); }
    }

    private static class TestAccountsRepo implements AccountsRepo {
        final Set<UUID> linked = new HashSet<>();
        final Map<UUID, Long> active = new HashMap<>();
        final Map<UUID, Long> any = new HashMap<>();
        final Map<Long, LinkedHashSet<UUID>> profiles = new HashMap<>();
        final Map<String, Integer> counts = new HashMap<>();
        UUID lastActivatedProfile;
        Long lastActivatedDiscord;

        @Override public void link(long discordId, UUID profileUuid) { activate(discordId, profileUuid); }
        @Override public void reserve(long discordId, UUID profileUuid) {
            profiles.computeIfAbsent(discordId, k -> new LinkedHashSet<>()).add(profileUuid);
        }
        @Override public void activate(long discordId, UUID profileUuid) {
            linked.add(profileUuid);
            active.put(profileUuid, discordId);
            profiles.computeIfAbsent(discordId, k -> new LinkedHashSet<>()).add(profileUuid);
            lastActivatedProfile = profileUuid;
            lastActivatedDiscord = discordId;
        }
        @Override public boolean isLinked(UUID profileUuid) { return linked.contains(profileUuid); }
        @Override public Optional<Long> findDiscordForProfile(UUID profileUuid) { return Optional.ofNullable(active.get(profileUuid)); }
        @Override public Optional<Long> findAnyDiscordForProfile(UUID profileUuid) { return Optional.ofNullable(any.getOrDefault(profileUuid, active.get(profileUuid))); }
        @Override public Set<UUID> findProfilesForDiscord(long discordId) { return profiles.getOrDefault(discordId, new LinkedHashSet<>()); }
        @Override public int countByDiscordAndPlatform(long discordId, String platform, boolean includeReserved) { return counts.getOrDefault(key(discordId, platform), 0); }
        @Override public void unlinkByProfile(UUID profileUuid) { linked.remove(profileUuid); active.remove(profileUuid); }
        @Override public void unlinkByDiscord(long discordId) { profiles.remove(discordId); }
        @Override public void unlinkByDiscordAndProfile(long discordId, UUID profileUuid) { var set = profiles.get(discordId); if (set != null) set.remove(profileUuid); }
    }

    private static class RecordingTokensRepo implements TokensRepo {
        final AtomicReference<Long> savedDiscordId = new AtomicReference<>();
        final AtomicReference<String> savedAccess = new AtomicReference<>();

        @Override public void save(long discordId, String accessToken, String refreshToken, Instant expiresAt, String tokenType, String scope) {
            savedDiscordId.set(discordId);
            savedAccess.set(accessToken);
        }
        @Override public Optional<TokenView> find(long discordId) { return Optional.empty(); }
    }

    private static class RecordingOAuth implements OAuthPort {
        @Override public String buildAuthUrl(String state, String redirectUri) { return "url"; }
        @Override public TokenSet exchangeCode(String code, String redirectUri) { return new TokenSet("code-access", "refresh", Instant.now().plusSeconds(60), "Bearer", "identify applications.commands"); }
        @Override public DiscordUser fetchUser(String accessToken) { return new DiscordUser(999L, "user", "global", "mail", "avatar"); }
    }

    private static class RecordingDiscordUserRepo implements DiscordUserRepo {
        final Set<Long> upserted = new HashSet<>();
        final Map<Long, Boolean> commandsInstalled = new HashMap<>();
        @Override public void upsertUser(long discordId, String username, String globalName, String email, String avatarHash) { upserted.add(discordId); }
        @Override public void setCommandsInstalled(long discordId, boolean installed) { commandsInstalled.put(discordId, installed); }
    }

    private static class RecordingDm implements DiscordDmPort {
        Long lastDiscordId;
        UUID lastProfile;
        String lastName;
        String lastIp;
        Long firstLoginDiscordId;
        UUID firstLoginProfile;

        @Override public void sendIpConfirmDm(long discordId, UUID profileUuid, String username, String ip) {
            lastDiscordId = discordId;
            lastProfile = profileUuid;
            lastName = username;
            lastIp = ip;
        }
        @Override public void sendFinalizeOAuthLink(long discordId, String loginUrl) {}
        @Override public void sendFirstLoginDm(long discordId, UUID profileUuid, String playerName, String publicUrl) {
            firstLoginDiscordId = discordId;
            firstLoginProfile = profileUuid;
        }
    }

    private static class NoopDm implements DiscordDmPort {
        @Override public void sendIpConfirmDm(long discordId, UUID profileUuid, String username, String ip) {}
        @Override public void sendFinalizeOAuthLink(long discordId, String loginUrl) {}
        @Override public void sendFirstLoginDm(long discordId, UUID profileUuid, String playerName, String publicUrl) {}
    }

    private static class StubBanProgressRepo implements BanProgressRepo {
        @Override public Optional<Record> findByIp(String ip) { return Optional.empty(); }
        @Override public void upsert(String ip, int attempts, long lastAttemptEpochSec, long lastBanUntilEpochSec) {}
        @Override public void reset(String ip) {}
    }
}
