package ua.beengoo.logdo2.api.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ua.beengoo.logdo2.api.spi.repo.AccountsRepo;
import ua.beengoo.logdo2.api.spi.repo.DiscordUserRepo;
import ua.beengoo.logdo2.api.spi.repo.ProfileRepo;
import ua.beengoo.logdo2.api.spi.repo.TokensRepo;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("LogDO2ProfileFactory Tests")
class LogDO2ProfileFactoryTest {

    @Mock
    private AccountsRepo accountsRepo;

    @Mock
    private ProfileRepo profileRepo;

    @Mock
    private TokensRepo tokensRepo;

    @Mock
    private DiscordUserRepo discordUserRepo;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Should create profile from Discord ID with valid token and linked accounts")
    void testFromDiscordIdWithTokenAndLinks() {
        long discordId = 123456789L;
        UUID mcUuid = UUID.randomUUID();

        // Mock token data
        TokensRepo.TokenView tokenView = new TokensRepo.TokenView(
            "access-token",
            "refresh-token",
            Instant.now().plusSeconds(3600),
            "Bearer",
            "identify email"
        );
        when(tokensRepo.find(discordId)).thenReturn(Optional.of(tokenView));

        // Mock Discord user data
        long createdAt = System.currentTimeMillis() / 1000;
        when(discordUserRepo.findCreatedAtByDiscordId(discordId)).thenReturn(Optional.of(createdAt));
        when(discordUserRepo.findProfileIdByDiscordId(discordId)).thenReturn(Optional.of("random-dude-123"));

        // Mock linked accounts
        Map<UUID, Boolean> linkedProfiles = new HashMap<>();
        linkedProfiles.put(mcUuid, true); // Primary account
        when(accountsRepo.findLinksWithPrimaryFlag(discordId)).thenReturn(linkedProfiles);

        // Mock Minecraft profile data
        when(profileRepo.findNameByUuid(mcUuid)).thenReturn(Optional.of("TestPlayer"));
        when(profileRepo.findPlatform(mcUuid)).thenReturn(Optional.of("JAVA"));
        when(profileRepo.findLastConfirmedIp(mcUuid)).thenReturn(Optional.of("192.168.1.1"));

        // Execute
        Optional<LogDO2Profile> result = LogDO2ProfileFactory.fromDiscordId(
            discordId, accountsRepo, profileRepo, tokensRepo, discordUserRepo
        );

        // Verify
        assertTrue(result.isPresent(), "Profile should be present");

        LogDO2Profile profile = result.get();
        assertEquals("random-dude-123", profile.getProfileId(), "Profile ID should match");
        assertEquals(LogDO2ProfileStatus.AUTHORIZED, profile.getProfileStatus(), "Status should be AUTHORIZED");
        assertEquals(1, profile.getLinkInfo().size(), "Should have 1 linked account");

        LinkInfo linkInfo = profile.getLinkInfo().get(0);
        assertTrue(linkInfo.isPrimary(), "Link should be marked as primary");
        assertEquals("TestPlayer", linkInfo.getMinecraftProfile().getName(), "Minecraft name should match");
        assertEquals("JAVA", linkInfo.getMinecraftProfile().getPlatform(), "Platform should be JAVA");
        assertEquals("192.168.1.1", linkInfo.getMinecraftProfile().getLastValidIp(), "Last IP should match");

        // Verify Discord profile
        DiscordProfile discordProfile = profile.getDiscordProfile();
        assertTrue(discordProfile.isUserAuthenticated(), "User should be authenticated");
        assertEquals(discordId, discordProfile.getOAuthInfo().getDiscordId(), "Discord ID should match");
        assertEquals("access-token", discordProfile.getOAuthInfo().getAccessToken(), "Access token should match");
    }

    @Test
    @DisplayName("Should create profile from Discord ID without tokens")
    void testFromDiscordIdWithoutTokens() {
        long discordId = 123456789L;

        // Mock no tokens found
        when(tokensRepo.find(discordId)).thenReturn(Optional.empty());
        when(discordUserRepo.findCreatedAtByDiscordId(discordId)).thenReturn(Optional.of(1000L));
        when(discordUserRepo.findProfileIdByDiscordId(discordId)).thenReturn(Optional.empty());

        // Mock no linked accounts
        when(accountsRepo.findLinksWithPrimaryFlag(discordId)).thenReturn(new HashMap<>());

        // Execute
        Optional<LogDO2Profile> result = LogDO2ProfileFactory.fromDiscordId(
            discordId, accountsRepo, profileRepo, tokensRepo, discordUserRepo
        );

        // Verify
        assertTrue(result.isPresent(), "Profile should be present");

        LogDO2Profile profile = result.get();
        assertEquals(String.valueOf(discordId), profile.getProfileId(), "Profile ID should default to Discord ID");
        assertEquals(LogDO2ProfileStatus.UNAUTHORIZED, profile.getProfileStatus(), "Status should be UNAUTHORIZED");
        assertTrue(profile.getLinkInfo().isEmpty(), "Should have no linked accounts");

        // Verify Discord OAuth info has empty tokens
        DiscordOAuthInfo oauthInfo = profile.getDiscordProfile().getOAuthInfo();
        assertEquals("", oauthInfo.getAccessToken(), "Access token should be empty");
        assertEquals("", oauthInfo.getRefreshToken(), "Refresh token should be empty");
    }

    @Test
    @DisplayName("Should create profile with multiple linked accounts")
    void testFromDiscordIdWithMultipleLinks() {
        long discordId = 123456789L;
        UUID mcUuid1 = UUID.randomUUID();
        UUID mcUuid2 = UUID.randomUUID();
        UUID mcUuid3 = UUID.randomUUID();

        // Mock token data
        when(tokensRepo.find(discordId)).thenReturn(Optional.of(
            new TokensRepo.TokenView("token", "refresh", Instant.now().plusSeconds(3600), "Bearer", "identify")
        ));
        when(discordUserRepo.findCreatedAtByDiscordId(discordId)).thenReturn(Optional.of(1000L));
        when(discordUserRepo.findProfileIdByDiscordId(discordId)).thenReturn(Optional.of("profile-456"));

        // Mock multiple linked accounts
        Map<UUID, Boolean> linkedProfiles = new HashMap<>();
        linkedProfiles.put(mcUuid1, true);  // Primary
        linkedProfiles.put(mcUuid2, false); // Not primary
        linkedProfiles.put(mcUuid3, false); // Not primary
        when(accountsRepo.findLinksWithPrimaryFlag(discordId)).thenReturn(linkedProfiles);

        // Mock Minecraft profile data
        when(profileRepo.findNameByUuid(mcUuid1)).thenReturn(Optional.of("Player1"));
        when(profileRepo.findPlatform(mcUuid1)).thenReturn(Optional.of("JAVA"));
        when(profileRepo.findLastConfirmedIp(mcUuid1)).thenReturn(Optional.of("192.168.1.1"));

        when(profileRepo.findNameByUuid(mcUuid2)).thenReturn(Optional.of("Player2"));
        when(profileRepo.findPlatform(mcUuid2)).thenReturn(Optional.of("BEDROCK"));
        when(profileRepo.findLastConfirmedIp(mcUuid2)).thenReturn(Optional.of("192.168.1.2"));

        when(profileRepo.findNameByUuid(mcUuid3)).thenReturn(Optional.of("Player3"));
        when(profileRepo.findPlatform(mcUuid3)).thenReturn(Optional.of("JAVA"));
        when(profileRepo.findLastConfirmedIp(mcUuid3)).thenReturn(Optional.of("192.168.1.3"));

        // Execute
        Optional<LogDO2Profile> result = LogDO2ProfileFactory.fromDiscordId(
            discordId, accountsRepo, profileRepo, tokensRepo, discordUserRepo
        );

        // Verify
        assertTrue(result.isPresent(), "Profile should be present");

        LogDO2Profile profile = result.get();
        assertEquals(3, profile.getLinkInfo().size(), "Should have 3 linked accounts");

        // Verify primary link exists
        long primaryCount = profile.getLinkInfo().stream().filter(LinkInfo::isPrimary).count();
        assertEquals(1, primaryCount, "Should have exactly 1 primary link");
    }

    @Test
    @DisplayName("Should handle missing Minecraft profile data gracefully")
    void testFromDiscordIdWithMissingMinecraftData() {
        long discordId = 123456789L;
        UUID mcUuid = UUID.randomUUID();

        // Mock token data
        when(tokensRepo.find(discordId)).thenReturn(Optional.of(
            new TokensRepo.TokenView("token", "refresh", Instant.now().plusSeconds(3600), "Bearer", "identify")
        ));
        when(discordUserRepo.findCreatedAtByDiscordId(discordId)).thenReturn(Optional.of(1000L));
        when(discordUserRepo.findProfileIdByDiscordId(discordId)).thenReturn(Optional.of("random-dude-337"));

        // Mock linked account
        Map<UUID, Boolean> linkedProfiles = new HashMap<>();
        linkedProfiles.put(mcUuid, false);
        when(accountsRepo.findLinksWithPrimaryFlag(discordId)).thenReturn(linkedProfiles);

        // Mock missing Minecraft profile data (empty optionals)
        when(profileRepo.findNameByUuid(mcUuid)).thenReturn(Optional.empty());
        when(profileRepo.findPlatform(mcUuid)).thenReturn(Optional.empty());
        when(profileRepo.findLastConfirmedIp(mcUuid)).thenReturn(Optional.empty());

        // Execute
        Optional<LogDO2Profile> result = LogDO2ProfileFactory.fromDiscordId(
            discordId, accountsRepo, profileRepo, tokensRepo, discordUserRepo
        );

        // Verify
        assertTrue(result.isPresent(), "Profile should be present");

        LogDO2Profile profile = result.get();
        assertEquals(1, profile.getLinkInfo().size(), "Should have 1 linked account");

        LinkInfo linkInfo = profile.getLinkInfo().get(0);
        assertEquals("Unknown", linkInfo.getMinecraftProfile().getName(), "Name should default to 'Unknown'");
        assertEquals("JAVA", linkInfo.getMinecraftProfile().getPlatform(), "Platform should default to 'JAVA'");
        assertEquals("", linkInfo.getMinecraftProfile().getLastValidIp(), "Last IP should default to empty string");
    }

    // ===== fromMinecraftUuid Tests =====

    @Test
    @DisplayName("Should create profile from Minecraft UUID when linked")
    void testFromMinecraftUuidWhenLinked() {
        UUID mcUuid = UUID.randomUUID();
        long discordId = 123456789L;

        // Mock that this Minecraft UUID is linked to a Discord account
        when(accountsRepo.findDiscordForProfile(mcUuid)).thenReturn(Optional.of(discordId));

        // Mock the rest of the data (same as fromDiscordId test)
        when(tokensRepo.find(discordId)).thenReturn(Optional.of(
            new TokensRepo.TokenView("token", "refresh", Instant.now().plusSeconds(3600), "Bearer", "identify")
        ));
        when(discordUserRepo.findCreatedAtByDiscordId(discordId)).thenReturn(Optional.of(1000L));
        when(discordUserRepo.findProfileIdByDiscordId(discordId)).thenReturn(Optional.of("profile-abc"));

        Map<UUID, Boolean> linkedProfiles = new HashMap<>();
        linkedProfiles.put(mcUuid, true);
        when(accountsRepo.findLinksWithPrimaryFlag(discordId)).thenReturn(linkedProfiles);

        when(profileRepo.findNameByUuid(mcUuid)).thenReturn(Optional.of("TestPlayer"));
        when(profileRepo.findPlatform(mcUuid)).thenReturn(Optional.of("JAVA"));
        when(profileRepo.findLastConfirmedIp(mcUuid)).thenReturn(Optional.of("192.168.1.1"));

        // Execute
        Optional<LogDO2Profile> result = LogDO2ProfileFactory.fromMinecraftUuid(
            mcUuid, accountsRepo, profileRepo, tokensRepo, discordUserRepo
        );

        // Verify
        assertTrue(result.isPresent(), "Profile should be present");

        LogDO2Profile profile = result.get();
        assertEquals("profile-abc", profile.getProfileId(), "Profile ID should match");
        assertEquals(LogDO2ProfileStatus.AUTHORIZED, profile.getProfileStatus(), "Status should be AUTHORIZED");
        assertEquals(discordId, profile.getDiscordProfile().getOAuthInfo().getDiscordId(), "Discord ID should match");
    }

    @Test
    @DisplayName("Should return empty when Minecraft UUID is not linked")
    void testFromMinecraftUuidWhenNotLinked() {
        UUID mcUuid = UUID.randomUUID();

        // Mock that this Minecraft UUID is NOT linked to any Discord account
        when(accountsRepo.findDiscordForProfile(mcUuid)).thenReturn(Optional.empty());

        // Execute
        Optional<LogDO2Profile> result = LogDO2ProfileFactory.fromMinecraftUuid(
            mcUuid, accountsRepo, profileRepo, tokensRepo, discordUserRepo
        );

        // Verify
        assertFalse(result.isPresent(), "Profile should not be present for unlinked Minecraft UUID");

        // Verify that other repos were not called
        verify(tokensRepo, never()).find(anyLong());
        verify(profileRepo, never()).findNameByUuid(any());
    }

    @Test
    @DisplayName("Should set status to UNAUTHORIZED when user not authenticated")
    void testProfileStatusUnauthorizedNoAuth() {
        long discordId = 123456789L;

        // Mock no tokens (not authenticated)
        when(tokensRepo.find(discordId)).thenReturn(Optional.empty());
        when(discordUserRepo.findCreatedAtByDiscordId(discordId)).thenReturn(Optional.of(1000L));
        when(discordUserRepo.findProfileIdByDiscordId(discordId)).thenReturn(Optional.empty());

        // Mock linked accounts exist
        UUID mcUuid = UUID.randomUUID();
        Map<UUID, Boolean> linkedProfiles = new HashMap<>();
        linkedProfiles.put(mcUuid, false);
        when(accountsRepo.findLinksWithPrimaryFlag(discordId)).thenReturn(linkedProfiles);

        when(profileRepo.findNameByUuid(mcUuid)).thenReturn(Optional.of("Player"));
        when(profileRepo.findPlatform(mcUuid)).thenReturn(Optional.of("JAVA"));
        when(profileRepo.findLastConfirmedIp(mcUuid)).thenReturn(Optional.of("192.168.1.1"));

        // Execute
        Optional<LogDO2Profile> result = LogDO2ProfileFactory.fromDiscordId(
            discordId, accountsRepo, profileRepo, tokensRepo, discordUserRepo
        );

        // Verify
        assertTrue(result.isPresent(), "Profile should be present");
        assertEquals(LogDO2ProfileStatus.UNAUTHORIZED, result.get().getProfileStatus(),
            "Status should be UNAUTHORIZED when not authenticated");
    }

    @Test
    @DisplayName("Should set status to AUTHORIZED when authenticated with links")
    void testProfileStatusAuthorizedWithLinks() {
        long discordId = 123456789L;
        UUID mcUuid = UUID.randomUUID();

        // Mock tokens (authenticated)
        when(tokensRepo.find(discordId)).thenReturn(Optional.of(
            new TokensRepo.TokenView("token", "refresh", Instant.now().plusSeconds(3600), "Bearer", "identify")
        ));
        when(discordUserRepo.findCreatedAtByDiscordId(discordId)).thenReturn(Optional.of(1000L));
        when(discordUserRepo.findProfileIdByDiscordId(discordId)).thenReturn(Optional.of("profile-def"));

        // Mock linked accounts
        Map<UUID, Boolean> linkedProfiles = new HashMap<>();
        linkedProfiles.put(mcUuid, false);
        when(accountsRepo.findLinksWithPrimaryFlag(discordId)).thenReturn(linkedProfiles);

        when(profileRepo.findNameByUuid(mcUuid)).thenReturn(Optional.of("Player"));
        when(profileRepo.findPlatform(mcUuid)).thenReturn(Optional.of("JAVA"));
        when(profileRepo.findLastConfirmedIp(mcUuid)).thenReturn(Optional.of("192.168.1.1"));

        // Execute
        Optional<LogDO2Profile> result = LogDO2ProfileFactory.fromDiscordId(
            discordId, accountsRepo, profileRepo, tokensRepo, discordUserRepo
        );

        // Verify
        assertTrue(result.isPresent(), "Profile should be present");
        assertEquals(LogDO2ProfileStatus.AUTHORIZED, result.get().getProfileStatus(),
            "Status should be AUTHORIZED when authenticated with links");
    }

    @Test
    @DisplayName("Should set status to UNAUTHORIZED when authenticated but no links")
    void testProfileStatusUnauthorizedNoLinks() {
        long discordId = 123456789L;

        // Mock tokens (authenticated)
        when(tokensRepo.find(discordId)).thenReturn(Optional.of(
            new TokensRepo.TokenView("token", "refresh", Instant.now().plusSeconds(3600), "Bearer", "identify")
        ));
        when(discordUserRepo.findCreatedAtByDiscordId(discordId)).thenReturn(Optional.of(1000L));
        when(discordUserRepo.findProfileIdByDiscordId(discordId)).thenReturn(Optional.of("profile-ghi"));

        // Mock no linked accounts
        when(accountsRepo.findLinksWithPrimaryFlag(discordId)).thenReturn(new HashMap<>());

        // Execute
        Optional<LogDO2Profile> result = LogDO2ProfileFactory.fromDiscordId(
            discordId, accountsRepo, profileRepo, tokensRepo, discordUserRepo
        );

        // Verify
        assertTrue(result.isPresent(), "Profile should be present");
        assertEquals(LogDO2ProfileStatus.UNAUTHORIZED, result.get().getProfileStatus(),
            "Status should be UNAUTHORIZED when authenticated but no links");
    }

    // ===== Edge Cases =====

    @Test
    @DisplayName("Should handle created_at not found")
    void testHandleCreatedAtNotFound() {
        long discordId = 123456789L;

        when(tokensRepo.find(discordId)).thenReturn(Optional.empty());
        when(discordUserRepo.findCreatedAtByDiscordId(discordId)).thenReturn(Optional.empty());
        when(discordUserRepo.findProfileIdByDiscordId(discordId)).thenReturn(Optional.empty());
        when(accountsRepo.findLinksWithPrimaryFlag(discordId)).thenReturn(new HashMap<>());

        Optional<LogDO2Profile> result = LogDO2ProfileFactory.fromDiscordId(
            discordId, accountsRepo, profileRepo, tokensRepo, discordUserRepo
        );

        assertTrue(result.isPresent(), "Profile should be present");
        assertEquals(0L, result.get().getDiscordProfile().getOAuthInfo().getCreatedAt(),
            "Created at should default to 0 when not found");
    }
}