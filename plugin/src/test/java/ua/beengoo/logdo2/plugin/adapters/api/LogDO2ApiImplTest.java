package ua.beengoo.logdo2.plugin.adapters.api;

import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import ua.beengoo.logdo2.api.LogDO2User;
import ua.beengoo.logdo2.api.ports.*;
import ua.beengoo.logdo2.core.service.LoginService;
import ua.beengoo.logdo2.core.service.LoginStateService;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class LogDO2ApiImplTest {
    private static final MessagesPort MSG = new MessagesPort() {
        @Override public String raw(String path) { return path; }
        @Override public String mc(String path) { return path; }
        @Override public String mc(String path, Map<String, String> placeholders) { return path; }
    };

    @Test
    void getUserAggregatesProfilesAndSession() {
        var state = new LoginStateService(Duration.ofSeconds(120));
        var profiles = new RecordingProfileRepo();
        var accounts = new RecordingAccountsRepo();
        var log = Logger.getLogger("test");

        UUID primary = UUID.randomUUID();
        UUID alt = UUID.randomUUID();
        long activeDiscord = 111L;
        long anyDiscord = 222L;

        profiles.setName(primary, "Steve");
        profiles.setPlatform(primary, "BEDROCK");
        profiles.setLastIp(primary, "10.0.0.1");
        profiles.setName(alt, "Alex");
        profiles.setPlatform(alt, "JAVA");
        profiles.setLastIp(alt, "10.0.0.2");

        accounts.setActive(primary, activeDiscord);
        accounts.setAny(primary, anyDiscord);
        accounts.addActiveProfile(activeDiscord, primary);
        accounts.addActiveProfile(activeDiscord, alt);

        state.markPendingLogin(primary, "5.5.5.5", true);
        state.markPendingIpConfirm(primary, "20.0.0.1", activeDiscord);
        state.grantLimitBypass(primary);

        LoginService loginService = createLoginService(state, profiles, accounts, log);
        LogDO2ApiImpl api = new LogDO2ApiImpl(loginService, profiles, accounts, state, (JDA) null);

        LogDO2User user = api.getUser(primary);

        assertEquals(primary, user.profile().uuid());
        assertEquals("Steve", user.profile().name());
        assertEquals(LogDO2User.MinecraftPlatform.BEDROCK, user.profile().platform());
        assertEquals("10.0.0.1", user.profile().lastConfirmedIp());

        assertEquals(Optional.of(activeDiscord), user.discord().activeDiscordId());
        assertEquals(Optional.of(anyDiscord), user.discord().anyDiscordId());

        List<LogDO2User.LinkedProfile> linked = new ArrayList<>(user.discord().profiles());
        assertEquals(2, linked.size());
        assertTrue(linked.get(0).primary());
        assertEquals(primary, linked.get(0).profile().uuid());
        assertFalse(linked.get(1).primary());
        assertEquals(alt, linked.get(1).profile().uuid());
        assertEquals(LogDO2User.MinecraftPlatform.JAVA, linked.get(1).profile().platform());

        assertTrue(user.session().isPendingLogin());
        assertEquals("5.5.5.5", user.session().pendingLogin().get().ip());
        assertTrue(user.session().pendingLogin().get().bedrock());

        assertTrue(user.session().isPendingIpConfirm());
        assertEquals("20.0.0.1", user.session().pendingIpConfirm().get().newIp());
        assertEquals(activeDiscord, user.session().pendingIpConfirm().get().discordId());
        assertTrue(user.session().limitBypassGranted());
    }

    private LoginService createLoginService(LoginStateService state, RecordingProfileRepo profiles, RecordingAccountsRepo accounts, Logger log) {
        return new LoginService(
                new StubOAuth(),
                null,
                accounts,
                profiles,
                new StubTokensRepo(),
                state,
                log,
                "http://public",
                "http://public/cb",
                null,
                null,
                new StubBanProgressRepo(),
                false,
                10, 2.0, 100, 100,
                "",
                MSG,
                60,
                3,
                3,
                false,
                false
        );
    }

    private static class RecordingProfileRepo implements ProfileRepo {
        private final Map<UUID, String> names = new HashMap<>();
        private final Map<UUID, String> platforms = new HashMap<>();
        private final Map<UUID, String> ips = new HashMap<>();

        void setName(UUID uuid, String name) { names.put(uuid, name); }
        void setPlatform(UUID uuid, String platform) { platforms.put(uuid, platform); }
        void setLastIp(UUID uuid, String ip) { ips.put(uuid, ip); }

        @Override public Optional<String> findLastConfirmedIp(UUID profileUuid) { return Optional.ofNullable(ips.get(profileUuid)); }
        @Override public void updateLastConfirmedIp(UUID profileUuid, String ip) { ips.put(profileUuid, ip); }
        @Override public void upsertName(UUID profileUuid, String playerName) { names.put(profileUuid, playerName); }
        @Override public void updatePlatform(UUID profileUuid, String platform) { platforms.put(profileUuid, platform); }
        @Override public Optional<UUID> findUuidByName(String name) { return names.entrySet().stream().filter(e -> Objects.equals(e.getValue(), name)).map(Map.Entry::getKey).findFirst(); }
        @Override public Optional<String> findNameByUuid(UUID uuid) { return Optional.ofNullable(names.get(uuid)); }
        @Override public Optional<String> findPlatform(UUID uuid) { return Optional.ofNullable(platforms.get(uuid)); }
    }

    private static class RecordingAccountsRepo implements AccountsRepo {
        private final Map<UUID, Long> active = new HashMap<>();
        private final Map<UUID, Long> any = new HashMap<>();
        private final Map<Long, LinkedHashSet<UUID>> profiles = new HashMap<>();

        void setActive(UUID profile, long discord) { active.put(profile, discord); }
        void setAny(UUID profile, long discord) { any.put(profile, discord); }
        void addActiveProfile(long discordId, UUID profile) {
            profiles.computeIfAbsent(discordId, k -> new LinkedHashSet<>()).add(profile);
        }

        @Override public void link(long discordId, UUID profileUuid) { activate(discordId, profileUuid); }
        @Override public void reserve(long discordId, UUID profileUuid) {}
        @Override public void activate(long discordId, UUID profileUuid) { setActive(profileUuid, discordId); addActiveProfile(discordId, profileUuid); }
        @Override public boolean isLinked(UUID profileUuid) { return active.containsKey(profileUuid); }
        @Override public Optional<Long> findDiscordForProfile(UUID profileUuid) { return Optional.ofNullable(active.get(profileUuid)); }
        @Override public Optional<Long> findAnyDiscordForProfile(UUID profileUuid) { return Optional.ofNullable(any.getOrDefault(profileUuid, active.get(profileUuid))); }
        @Override public Set<UUID> findProfilesForDiscord(long discordId) { return profiles.getOrDefault(discordId, new LinkedHashSet<>()); }
        @Override public int countByDiscordAndPlatform(long discordId, String platform, boolean includeReserved) { return profiles.getOrDefault(discordId, new LinkedHashSet<>()).size(); }
        @Override public void unlinkByProfile(UUID profileUuid) {}
        @Override public void unlinkByDiscord(long discordId) {}
        @Override public void unlinkByDiscordAndProfile(long discordId, UUID profileUuid) {}
    }

    private static class StubOAuth implements OAuthPort {
        @Override public String buildAuthUrl(String state, String redirectUri) { return ""; }
        @Override public TokenSet exchangeCode(String code, String redirectUri) { return new TokenSet("a", "r", Instant.now().plusSeconds(60), "Bearer", "identify"); }
        @Override public DiscordUser fetchUser(String accessToken) { return new DiscordUser(1L, "user", "global", "mail", "avatar"); }
    }

    private static class StubTokensRepo implements TokensRepo {
        @Override public void save(long discordId, String accessToken, String refreshToken, Instant expiresAt, String tokenType, String scope) {}
        @Override public Optional<TokenView> find(long discordId) { return Optional.empty(); }
    }

    private static class StubBanProgressRepo implements BanProgressRepo {
        @Override public Optional<Record> findByIp(String ip) { return Optional.empty(); }
        @Override public void upsert(String ip, int attempts, long lastAttemptEpochSec, long lastBanUntilEpochSec) {}
        @Override public void reset(String ip) {}
    }
}
