package ua.beengoo.logdo2.core.service;

import org.junit.jupiter.api.Test;
import ua.beengoo.logdo2.api.ports.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LoginServiceLimitBypassTest {

    private static final MessagesPort MSG = new MessagesPort() {
        @Override public String raw(String path) { return path; }
        @Override public String mc(String path) { return path; }
        @Override public String mc(String path, Map<String, String> placeholders) { return path; }
    };

    private static class FakeProfileRepo implements ProfileRepo {
        final Map<UUID, String> platform = new HashMap<>();
        @Override public Optional<String> findLastConfirmedIp(UUID profileUuid) { return Optional.empty(); }
        @Override public void updateLastConfirmedIp(UUID profileUuid, String ip) { }
        @Override public void upsertName(UUID profileUuid, String playerName) { }
        @Override public void updatePlatform(UUID profileUuid, String p) { platform.put(profileUuid, p); }
        @Override public Optional<UUID> findUuidByName(String name) { return Optional.empty(); }
        @Override public Optional<String> findNameByUuid(UUID uuid) { return Optional.empty(); }
        @Override public Optional<String> findPlatform(UUID uuid) { return Optional.ofNullable(platform.get(uuid)); }
    }

    private static class FakeAccountsRepo implements AccountsRepo {
        record Link(long discordId, UUID uuid, boolean active) {}
        private final Set<Link> links = new HashSet<>();
        private final FakeProfileRepo profiles;
        FakeAccountsRepo(FakeProfileRepo profiles) { this.profiles = profiles; }
        @Override public void link(long discordId, UUID profileUuid) { activate(discordId, profileUuid); }
        @Override public void reserve(long discordId, UUID profileUuid) { links.add(new Link(discordId, profileUuid, false)); }
        @Override public void activate(long discordId, UUID profileUuid) {
            // deactivate others for this profile
            links.removeIf(l -> l.uuid.equals(profileUuid));
            links.add(new Link(discordId, profileUuid, true));
        }
        @Override public boolean isLinked(UUID profileUuid) { return links.stream().anyMatch(l -> l.uuid.equals(profileUuid) && l.active); }
        @Override public Optional<Long> findDiscordForProfile(UUID profileUuid) {
            return links.stream().filter(l -> l.uuid.equals(profileUuid) && l.active).map(l -> l.discordId).findFirst();
        }
        @Override public Optional<Long> findAnyDiscordForProfile(UUID profileUuid) {
            return links.stream().filter(l -> l.uuid.equals(profileUuid)).map(l -> l.discordId).findFirst();
        }
        @Override public Set<UUID> findProfilesForDiscord(long discordId) {
            Set<UUID> s = new HashSet<>();
            links.stream().filter(l -> l.discordId == discordId && l.active).forEach(l -> s.add(l.uuid));
            return s;
        }
        @Override public int countByDiscordAndPlatform(long discordId, String platform, boolean includeReserved) {
            return (int) links.stream()
                    .filter(l -> l.discordId == discordId)
                    .filter(l -> includeReserved || l.active)
                    .filter(l -> platform.equalsIgnoreCase(profiles.platform.getOrDefault(l.uuid, "")))
                    .count();
        }
        @Override public void unlinkByProfile(UUID profileUuid) { links.removeIf(l -> l.uuid.equals(profileUuid)); }
        @Override public void unlinkByDiscord(long discordId) { links.removeIf(l -> l.discordId == discordId); }
        @Override public void unlinkByDiscordAndProfile(long discordId, UUID profileUuid) { links.removeIf(l -> l.discordId == discordId && l.uuid.equals(profileUuid)); }
    }

    private static class NoopTokensRepo implements TokensRepo {
        @Override public void save(long discordId, String accessToken, String refreshToken, Instant expiresAt, String tokenType, String scope) {}
        @Override public Optional<TokenView> find(long discordId) { return Optional.empty(); }
    }

    private static class DummyOAuth implements OAuthPort {
        @Override public String buildAuthUrl(String state, String redirectUri) { return ""; }
        @Override public TokenSet exchangeCode(String code, String redirectUri) { return new TokenSet("a","r", Instant.now().plusSeconds(60), "","identify"); }
        @Override public DiscordUser fetchUser(String accessToken) { return new DiscordUser(111L, "u","g","e","a"); }
    }

    @Test
    void slashLoginBlocksWhenAtLimitWithoutBypass() {
        var state = new LoginStateService(Duration.ofSeconds(60));
        var profiles = new FakeProfileRepo();
        var accounts = new FakeAccountsRepo(profiles);

        // Pre-populate an active Bedrock link for discord 111 to hit limit=1
        UUID existing = UUID.randomUUID();
        profiles.updatePlatform(existing, "BEDROCK");
        accounts.activate(111L, existing);

        var svc = new LoginService(new DummyOAuth(), null,
                accounts, profiles, new NoopTokensRepo(), state, java.util.logging.Logger.getLogger("test"),
                "http://x", "http://x/cb",
                null,
                null,
                null,
                false,
                10, 2.0, 100, 100,
                "",
                MSG,
                60,
                1,
                1,
                true,
                false);

        // Prepare a pending code for a different profile
        UUID target = UUID.randomUUID();
        String code = state.createOneTimeCode(target, "1.1.1.1", "Test");

        boolean ok = svc.onDiscordSlashLogin(code, 111L);
        assertFalse(ok, "Should block reservation when at limit and no bypass");
    }

    @Test
    void slashLoginAllowsWithBypassEvenAtLimit() {
        var state = new LoginStateService(Duration.ofSeconds(60));
        var profiles = new FakeProfileRepo();
        var accounts = new FakeAccountsRepo(profiles);

        // Pre-populate an active Bedrock link for discord 222 to hit limit=1
        UUID existing = UUID.randomUUID();
        profiles.updatePlatform(existing, "BEDROCK");
        accounts.activate(222L, existing);

        var svc = new LoginService(new DummyOAuth(), null,
                accounts, profiles, new NoopTokensRepo(), state, java.util.logging.Logger.getLogger("test"),
                "http://x", "http://x/cb",
                null,
                null,
                null,
                false,
                10, 2.0, 100, 100,
                "",
                MSG,
                60,
                1,
                1,
                true,
                false);

        UUID target = UUID.randomUUID();
        state.grantLimitBypass(target);
        String code = state.createOneTimeCode(target, "1.1.1.1", "Test");

        boolean ok = svc.onDiscordSlashLogin(code, 222L);
        assertTrue(ok, "Bypass should allow reservation despite limit");
    }

    @Test
    void reservedDoesNotCountWhenIncludeReservedFalse() {
        var state = new LoginStateService(Duration.ofSeconds(60));
        var profiles = new FakeProfileRepo();
        var accounts = new FakeAccountsRepo(profiles);

        // Pre-populate a RESERVED Bedrock link for discord 333
        UUID existing = UUID.randomUUID();
        profiles.updatePlatform(existing, "BEDROCK");
        accounts.reserve(333L, existing);

        var svc = new LoginService(new DummyOAuth(), null,
                accounts, profiles, new NoopTokensRepo(), state, java.util.logging.Logger.getLogger("test"),
                "http://x", "http://x/cb",
                null,
                null,
                null,
                false,
                10, 2.0, 100, 100,
                "",
                MSG,
                60,
                1,
                1,
                false, // includeReserved = false
                false);

        UUID target = UUID.randomUUID();
        String code = state.createOneTimeCode(target, "1.1.1.1", "Test");

        boolean ok = svc.onDiscordSlashLogin(code, 333L);
        assertTrue(ok, "Reserved should be ignored when includeReserved=false");
    }

    @Test
    void reservedCountsWhenIncludeReservedTrue() {
        var state = new LoginStateService(Duration.ofSeconds(60));
        var profiles = new FakeProfileRepo();
        var accounts = new FakeAccountsRepo(profiles);

        // Pre-populate a RESERVED Bedrock link for discord 444
        UUID existing = UUID.randomUUID();
        profiles.updatePlatform(existing, "BEDROCK");
        accounts.reserve(444L, existing);

        var svc = new LoginService(new DummyOAuth(), null,
                accounts, profiles, new NoopTokensRepo(), state, java.util.logging.Logger.getLogger("test"),
                "http://x", "http://x/cb",
                null,
                null,
                null,
                false,
                10, 2.0, 100, 100,
                "",
                MSG,
                60,
                1,
                1,
                true, // includeReserved = true
                false);

        UUID target = UUID.randomUUID();
        String code = state.createOneTimeCode(target, "1.1.1.1", "Test");

        boolean ok = svc.onDiscordSlashLogin(code, 444L);
        assertFalse(ok, "Reserved should count when includeReserved=true");
    }
}
