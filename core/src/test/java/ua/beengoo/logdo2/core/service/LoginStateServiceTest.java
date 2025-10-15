package ua.beengoo.logdo2.core.service;

import org.junit.jupiter.api.Test;
import ua.beengoo.logdo2.api.provider.Properties;
import ua.beengoo.logdo2.api.provider.PropertiesProvider;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LoginStateServiceTest {

    private static final PropertiesProvider props = new PropertiesProvider() {
        @Override
        public ua.beengoo.logdo2.api.provider.Properties getSnapshot() {
            return new Properties(
                    1,
                    1, 1,
                    true, true, 0, 2, 4, 100,
                    true
            );
        }
    };

    @Test
    void createAndConsumeOAuthState() {
        var svc = new LoginStateService(props);
        var uuid = UUID.randomUUID();
        String token = svc.createOAuthState(uuid, "1.2.3.4", "Steve", false);

        assertTrue(svc.hasOAuthState(token));
        var st = svc.consumeOAuthState(token);
        assertEquals(uuid, st.uuid());
        assertEquals("1.2.3.4", st.ip());
        assertEquals("Steve", st.name());
        assertFalse(st.bedrock());
        assertFalse(svc.hasOAuthState(token));
    }

    @Test
    void oneTimeCodeIsUniqueAndConsumable() {
        var svc = new LoginStateService(props);
        var uuid = UUID.randomUUID();
        String code = svc.createOneTimeCode(uuid, "1.2.3.4", "Alex");

        assertNotNull(svc.consumeOneTimeCode(code));
        assertNull(svc.consumeOneTimeCode(code)); // consumed
    }

    @Test
    void pendingLoginAndIpFlags() {
        var svc = new LoginStateService(props);
        var uuid = UUID.randomUUID();
        svc.markPendingLogin(uuid, "1.2.3.4", false);
        assertTrue(svc.isPendingLogin(uuid));
        svc.clearPendingLogin(uuid);
        assertFalse(svc.isPendingLogin(uuid));

        svc.markPendingIpConfirm(uuid, "5.6.7.8", 123L);
        assertTrue(svc.isPendingIpConfirm(uuid));
        assertNotNull(svc.consumePendingIpConfirm(uuid));
        assertFalse(svc.isPendingIpConfirm(uuid));
    }

    @Test
    void recentBedrockCodeAfterLeaveIsResent() {
        var svc = new LoginStateService(props);
        var uuid = UUID.randomUUID();
        // Create a code (also records shown)
        String code = svc.createOneTimeCode(uuid, "1.2.3.4", "Alex");
        // Mark player left
        svc.recordBedrockLeave(uuid);
        // Within 60s, we can retrieve the same code
        var reused = svc.recentBedrockCodeAfterLeave(uuid, java.time.Duration.ofSeconds(60));
        assertTrue(reused.isPresent());
        assertEquals(code, reused.get());
    }

    @Test
    void limitBypassGrantHasConsumeCycle() {
        var svc = new LoginStateService(props);
        var uuid = UUID.randomUUID();
        assertFalse(svc.hasLimitBypass(uuid));
        assertFalse(svc.consumeLimitBypass(uuid));
        svc.grantLimitBypass(uuid);
        assertTrue(svc.hasLimitBypass(uuid));
        assertTrue(svc.consumeLimitBypass(uuid));
        assertFalse(svc.hasLimitBypass(uuid));
        assertFalse(svc.consumeLimitBypass(uuid));
    }


    @Test
    void recentBedrockCodeExpiresAfterMaxAge() throws Exception {
        var svc = new LoginStateService(props);
        var uuid = UUID.randomUUID();
        svc.createOneTimeCode(uuid, "1.2.3.4", "Alex");
        svc.recordBedrockLeave(uuid);
        assertTrue(svc.recentBedrockCodeAfterLeave(uuid, java.time.Duration.ofSeconds(60)).isPresent());
        Thread.sleep(5);
        assertTrue(svc.recentBedrockCodeAfterLeave(uuid, java.time.Duration.ofMillis(1)).isEmpty());
    }

    @Test
    void bedrockCodeCannotBeConsumedAfterReuseWindow() throws Exception {
        var svc = new LoginStateService(props);
        var uuid = UUID.randomUUID();
        String code = svc.createOneTimeCode(uuid, "1.2.3.4", "Alex");
        svc.recordBedrockLeave(uuid);
        Thread.sleep(1015);
        assertNull(svc.consumeOneTimeCode(code));
    }
}
