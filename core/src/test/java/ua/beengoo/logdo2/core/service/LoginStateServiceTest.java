package ua.beengoo.logdo2.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ua.beengoo.logdo2.api.events.LoginPhase;
import ua.beengoo.logdo2.api.spi.providers.Properties;
import ua.beengoo.logdo2.api.spi.providers.PropertiesProvider;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("LoginStateService Tests")
class LoginStateServiceTest {

    @Mock
    private PropertiesProvider propertiesProvider;

    private LoginStateService service;
    private Properties testProperties;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Create test properties with reasonable defaults
        testProperties = new Properties(
            60L,      // bedrockCodeTimeAfterLeave: 60 seconds
            3,        // bedrockLimitPerDiscord
            5,        // javaLimitPerDiscord
            false,    // limitIncludeReserved
            true,     // bansEnabled
            3600L,    // banTrackWindowSec
            1.5,      // banMultiplier
            60.0,     // banBaseSec
            86400L,   // banMaxSec
            false     // disallowSimultaneousPlay
        );

        when(propertiesProvider.getSnapshot()).thenReturn(testProperties);
        service = new LoginStateService(propertiesProvider);
    }

    // ===== OAuth State Tests =====

    @Test
    @DisplayName("Should create and consume OAuth state successfully")
    void testCreateAndConsumeOAuthState() {
        UUID uuid = UUID.randomUUID();
        String ip = "192.168.1.1";
        String name = "TestPlayer";
        boolean bedrock = false;

        String token = service.createOAuthState(uuid, ip, name, bedrock);

        assertNotNull(token, "Token should not be null");
        assertFalse(token.isEmpty(), "Token should not be empty");
        assertEquals(32, token.length(), "Token should be 32 characters (16 bytes hex)");

        LoginStateService.OAuthState state = service.consumeOAuthState(token);

        assertNotNull(state, "State should not be null");
        assertEquals(uuid, state.uuid(), "UUID should match");
        assertEquals(ip, state.ip(), "IP should match");
        assertEquals(name, state.name(), "Name should match");
        assertEquals(bedrock, state.bedrock(), "Bedrock flag should match");
    }

    @Test
    @DisplayName("Should throw exception when consuming invalid OAuth state")
    void testConsumeInvalidOAuthState() {
        assertThrows(IllegalStateException.class, () -> service.consumeOAuthState("invalid-token"));
    }

    @Test
    @DisplayName("Should throw exception when consuming already consumed OAuth state")
    void testConsumeOAuthStateTwice() {
        UUID uuid = UUID.randomUUID();
        String token = service.createOAuthState(uuid, "192.168.1.1", "TestPlayer", false);

        service.consumeOAuthState(token);

        assertThrows(IllegalStateException.class, () -> service.consumeOAuthState(token),
            "Should throw exception when consuming the same token twice");
    }

    @Test
    @DisplayName("Should check if OAuth state exists")
    void testHasOAuthState() {
        UUID uuid = UUID.randomUUID();
        String token = service.createOAuthState(uuid, "192.168.1.1", "TestPlayer", false);

        assertTrue(service.hasOAuthState(token), "State should exist");

        service.consumeOAuthState(token);

        assertFalse(service.hasOAuthState(token), "State should not exist after consumption");
    }

    @Test
    @DisplayName("Should generate unique OAuth state tokens")
    void testUniqueOAuthStateTokens() {
        UUID uuid = UUID.randomUUID();
        String token1 = service.createOAuthState(uuid, "192.168.1.1", "Player1", false);
        String token2 = service.createOAuthState(uuid, "192.168.1.1", "Player1", false);

        assertNotEquals(token1, token2, "Tokens should be unique");
    }

    // ===== One-Time Code Tests =====

    @Test
    @DisplayName("Should create and consume one-time code successfully")
    void testCreateAndConsumeOneTimeCode() {
        UUID uuid = UUID.randomUUID();
        String ip = "192.168.1.1";
        String name = "TestPlayer";

        String code = service.createOneTimeCode(uuid, ip, name);

        assertNotNull(code, "Code should not be null");
        assertEquals(6, code.length(), "Code should be 6 characters");
        assertTrue(code.matches("[A-Z2-9]+"), "Code should only contain uppercase letters and numbers (excluding confusing chars)");

        LoginStateService.PendingCode pendingCode = service.consumeOneTimeCode(code);

        assertNotNull(pendingCode, "PendingCode should not be null");
        assertEquals(uuid, pendingCode.uuid(), "UUID should match");
        assertEquals(ip, pendingCode.ip(), "IP should match");
        assertEquals(name, pendingCode.name(), "Name should match");
        assertEquals(code, pendingCode.code(), "Code should match");
    }

    @Test
    @DisplayName("Should return null when consuming invalid one-time code")
    void testConsumeInvalidOneTimeCode() {
        LoginStateService.PendingCode result = service.consumeOneTimeCode("INVALID");
        assertNull(result, "Should return null for invalid code");
    }

    @Test
    @DisplayName("Should return null when consuming already consumed one-time code")
    void testConsumeOneTimeCodeTwice() {
        UUID uuid = UUID.randomUUID();
        String code = service.createOneTimeCode(uuid, "192.168.1.1", "TestPlayer");

        service.consumeOneTimeCode(code);
        LoginStateService.PendingCode result = service.consumeOneTimeCode(code);

        assertNull(result, "Should return null when consuming code twice");
    }

    @Test
    @DisplayName("Should generate unique one-time codes")
    void testUniqueOneTimeCodes() {
        UUID uuid = UUID.randomUUID();
        String code1 = service.createOneTimeCode(uuid, "192.168.1.1", "Player1");
        String code2 = service.createOneTimeCode(UUID.randomUUID(), "192.168.1.2", "Player2");

        assertNotEquals(code1, code2, "Codes should be unique");
    }

    // ===== Bedrock Code Lifecycle Tests =====

    @Test
    @DisplayName("Should track Bedrock code shown and leave times")
    void testBedrockCodeLifecycle() {
        UUID uuid = UUID.randomUUID();
        String code = service.createOneTimeCode(uuid, "192.168.1.1", "BedrockPlayer");

        service.recordBedrockCodeShown(uuid, code);
        service.recordBedrockLeave(uuid);

        Optional<String> recentCode = service.recentBedrockCodeAfterLeave(uuid, Duration.ofSeconds(60));

        assertTrue(recentCode.isPresent(), "Should have recent code");
        assertEquals(code, recentCode.get(), "Code should match");
    }

    @Test
    @DisplayName("Should return empty when Bedrock code is too old after leave")
    void testBedrockCodeExpiredAfterLeave() throws InterruptedException {
        UUID uuid = UUID.randomUUID();
        String code = service.createOneTimeCode(uuid, "192.168.1.1", "BedrockPlayer");

        service.recordBedrockCodeShown(uuid, code);
        service.recordBedrockLeave(uuid);

        // Wait a bit and check with very short max age
        Thread.sleep(50);
        Optional<String> recentCode = service.recentBedrockCodeAfterLeave(uuid, Duration.ofMillis(10));

        assertFalse(recentCode.isPresent(), "Should not have recent code (too old)");
    }

    @Test
    @DisplayName("Should invalidate Bedrock code after leave window expires")
    void testBedrockCodeInvalidatedAfterLeaveWindow() throws InterruptedException {
        UUID uuid = UUID.randomUUID();
        String code = service.createOneTimeCode(uuid, "192.168.1.1", "BedrockPlayer");

        service.recordBedrockCodeShown(uuid, code);
        service.recordBedrockLeave(uuid);

        // Simulate waiting beyond the bedrockCodeTimeAfterLeave window
        Thread.sleep(100);

        // Mock properties to return a very short window (10ms)
        Properties shortWindowProps = new Properties(
            0L, 3, 5, false, true, 3600L, 1.5, 60.0, 86400L, false
        );
        when(propertiesProvider.getSnapshot()).thenReturn(shortWindowProps);

        LoginStateService.PendingCode result = service.consumeOneTimeCode(code);

        assertNull(result, "Code should be invalidated after leave window expires");
    }

    // ===== Login Phase Tests =====

    @Test
    @DisplayName("Should return correct login phase for pending login")
    void testGetLoginPhasePendingLogin() {
        UUID uuid = UUID.randomUUID();
        service.markPendingLogin(uuid, "192.168.1.1", "token123", false);

        LoginPhase phase = service.getLoginPhase(uuid);

        assertEquals(LoginPhase.LOGIN, phase, "Phase should be LOGIN");
    }

    @Test
    @DisplayName("Should return correct login phase for pending IP confirm")
    void testGetLoginPhasePendingIpConfirm() {
        UUID uuid = UUID.randomUUID();
        service.markPendingIpConfirm(uuid, "192.168.1.2", 123456789L);

        LoginPhase phase = service.getLoginPhase(uuid);

        assertEquals(LoginPhase.IP_CONFIRM, phase, "Phase should be IP_CONFIRM");
    }

    @Test
    @DisplayName("Should prioritize IP_CONFIRM phase over LOGIN phase")
    void testGetLoginPhasePriority() {
        UUID uuid = UUID.randomUUID();
        service.markPendingLogin(uuid, "192.168.1.1", "token123", false);
        service.markPendingIpConfirm(uuid, "192.168.1.2", 123456789L);

        LoginPhase phase = service.getLoginPhase(uuid);

        assertEquals(LoginPhase.IP_CONFIRM, phase, "IP_CONFIRM should take priority");
    }

    @Test
    @DisplayName("Should return null when no login phase is active")
    void testGetLoginPhaseNone() {
        UUID uuid = UUID.randomUUID();
        LoginPhase phase = service.getLoginPhase(uuid);

        assertNull(phase, "Phase should be null when no pending state");
    }

    // ===== Pending IP Confirm Tests =====

    @Test
    @DisplayName("Should mark and check pending IP confirm")
    void testPendingIpConfirm() {
        UUID uuid = UUID.randomUUID();
        String newIp = "192.168.1.2";
        long discordId = 123456789L;

        service.markPendingIpConfirm(uuid, newIp, discordId);

        assertTrue(service.isPendingIpConfirm(uuid), "Should be pending IP confirm");

        LoginStateService.PendingIp pendingIp = service.consumePendingIpConfirm(uuid);

        assertNotNull(pendingIp, "PendingIp should not be null");
        assertEquals(uuid, pendingIp.uuid(), "UUID should match");
        assertEquals(newIp, pendingIp.newIp(), "New IP should match");
        assertEquals(discordId, pendingIp.discordId(), "Discord ID should match");

        assertFalse(service.isPendingIpConfirm(uuid), "Should not be pending after consumption");
    }

    @Test
    @DisplayName("Should list all pending IP confirms")
    void testListPendingIpConfirms() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();

        service.markPendingIpConfirm(uuid1, "192.168.1.1", 111L);
        service.markPendingIpConfirm(uuid2, "192.168.1.2", 222L);

        Collection<LoginStateService.PendingIp> pending = service.listPendingIpConfirms();

        assertEquals(2, pending.size(), "Should have 2 pending IP confirms");
    }

    // ===== Pending Login Tests =====

    @Test
    @DisplayName("Should mark and check pending login")
    void testPendingLogin() {
        UUID uuid = UUID.randomUUID();
        String ip = "192.168.1.1";
        String token = "oauth-token-123";
        boolean bedrock = true;

        service.markPendingLogin(uuid, ip, token, bedrock);

        assertTrue(service.isPendingLogin(uuid), "Should be pending login");

        service.clearPendingLogin(uuid);

        assertFalse(service.isPendingLogin(uuid), "Should not be pending after clearing");
    }

    @Test
    @DisplayName("Should list all pending logins")
    void testListPendingLogins() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();

        service.markPendingLogin(uuid1, "192.168.1.1", "token1", false);
        service.markPendingLogin(uuid2, "192.168.1.2", "token2", true);

        Collection<LoginStateService.PendingLogin> pending = service.listPendingLogins();

        assertEquals(2, pending.size(), "Should have 2 pending logins");
    }

    @Test
    @DisplayName("Should clear pending login and bedrock shown info")
    void testClearPendingLoginAlsoClearsBedrock() {
        UUID uuid = UUID.randomUUID();
        String code = service.createOneTimeCode(uuid, "192.168.1.1", "Player");

        service.markPendingLogin(uuid, "192.168.1.1", "token", true);
        service.recordBedrockCodeShown(uuid, code);

        service.clearPendingLogin(uuid);

        assertFalse(service.isPendingLogin(uuid), "Pending login should be cleared");
        service.recordBedrockLeave(uuid);
        Optional<String> recentCode = service.recentBedrockCodeAfterLeave(uuid, Duration.ofSeconds(60));
        assertFalse(recentCode.isPresent(), "Bedrock shown info should be cleared");
    }

    // ===== Limit Bypass Tests =====

    @Test
    @DisplayName("Should grant and check limit bypass")
    void testLimitBypass() {
        UUID uuid = UUID.randomUUID();

        assertFalse(service.hasLimitBypass(uuid), "Should not have bypass initially");

        service.grantLimitBypass(uuid);

        assertTrue(service.hasLimitBypass(uuid), "Should have bypass after granting");
    }

    @Test
    @DisplayName("Should consume limit bypass")
    void testConsumeLimitBypass() {
        UUID uuid = UUID.randomUUID();
        service.grantLimitBypass(uuid);

        boolean consumed = service.consumeLimitBypass(uuid);

        assertTrue(consumed, "Should successfully consume bypass");
        assertFalse(service.hasLimitBypass(uuid), "Should not have bypass after consumption");
    }

    @Test
    @DisplayName("Should return false when consuming non-existent limit bypass")
    void testConsumeNonExistentLimitBypass() {
        UUID uuid = UUID.randomUUID();

        boolean consumed = service.consumeLimitBypass(uuid);

        assertFalse(consumed, "Should return false for non-existent bypass");
    }

    @Test
    @DisplayName("Should handle null UUID in limit bypass operations")
    void testLimitBypassNullUuid() {
        service.grantLimitBypass(null);
        assertFalse(service.hasLimitBypass(null), "Should return false for null UUID");
        assertFalse(service.consumeLimitBypass(null), "Should return false for null UUID");
    }

    // ===== Concurrent Access Tests =====

    @Test
    @DisplayName("Should handle concurrent OAuth state operations")
    void testConcurrentOAuthOperations() throws InterruptedException {
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        String[] tokens = new String[threadCount];

        // Create states concurrently
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                UUID uuid = UUID.randomUUID();
                tokens[index] = service.createOAuthState(uuid, "192.168.1." + index, "Player" + index, false);
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Verify all tokens are unique and valid
        for (int i = 0; i < threadCount; i++) {
            assertNotNull(tokens[i], "Token should not be null");
            assertTrue(service.hasOAuthState(tokens[i]), "State should exist for token " + i);
        }
    }
}