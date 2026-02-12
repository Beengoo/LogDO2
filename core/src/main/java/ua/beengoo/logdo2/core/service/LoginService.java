package ua.beengoo.logdo2.core.service;

import lombok.extern.slf4j.Slf4j;
import ua.beengoo.logdo2.api.entity.WebServerInfo;
import ua.beengoo.logdo2.api.events.*;
import ua.beengoo.logdo2.api.spi.PlatformBridge;
import ua.beengoo.logdo2.api.spi.callbacks.LoginCallbacks;
import ua.beengoo.logdo2.api.spi.repo.*;
import ua.beengoo.logdo2.api.spi.providers.*;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

@Slf4j(topic = "LogDO2")
public class LoginService {
    private final OAuthProvider oauth;
    private DiscordMessagesProvider dm;
    private final AccountsRepo accounts;
    private final ProfileRepo profiles;
    private final TokensRepo tokens;
    private final LoginStateService state;
    private final DiscordUserRepo discordUserRepo;
    private WebServerInfo webServerInfo;
    private final PlatformBridge platform;
    private final LoginCallbacks callbacks;
    private final MessagesProvider msg;

    private final PropertiesProvider propertiesProvider;
    private final BanProgressRepo banProgressRepo;

    public LoginService(OAuthProvider oauth, DiscordMessagesProvider dm,
                        AccountsRepo accounts, ProfileRepo profiles, TokensRepo tokens,
                        LoginStateService state,
                        WebServerInfo webServerInfo,
                        DiscordUserRepo discordUserRepo,
                        BanProgressRepo banProgressRepo,
                        PropertiesProvider propertiesProvider,
                        MessagesProvider messages,
                        PlatformBridge platform,
                        LoginCallbacks callbacks) {
        this.oauth = oauth;
        this.dm = dm;
        this.accounts = accounts;
        this.profiles = profiles;
        this.tokens = tokens;
        this.state = state;
        this.webServerInfo = webServerInfo;
        this.discordUserRepo = discordUserRepo;
        this.msg = messages;
        this.platform = platform;
        this.callbacks = callbacks;

        this.banProgressRepo = banProgressRepo;
        this.propertiesProvider = propertiesProvider;
    }

    public void setDiscordDmPort(DiscordMessagesProvider dm) { this.dm = dm; }

    public void updateWebServerInfo(WebServerInfo webServerInfo) { this.webServerInfo = webServerInfo; }

    public void handlePlayerJoin(UUID uuid, String name, String currentIp, boolean bedrock) {
        profiles.upsertName(uuid, name);
        profiles.updatePlatform(uuid, bedrock ? "BEDROCK" : "JAVA");
        Properties props = propertiesProvider.getSnapshot();

        // Check if linked account requires re-authentication
        Optional<Long> linkedDiscord = accounts.findAnyDiscordForProfile(uuid);
        if (linkedDiscord.isPresent() && accounts.requiresReauth(linkedDiscord.get())) {
            // Profile is linked but Discord account requires re-auth
            // Start OAuth flow but link remains reserved to original Discord ID
            if (bedrock) {
                String code = state.createOneTimeCode(uuid, currentIp, name);
                state.recordBedrockCodeShown(uuid, code);
                state.markPendingLogin(uuid, currentIp, code, true);
                firePhaseEnter(uuid, LoginPhase.LOGIN, new LoginCallbacks.PlayerLoginData(true, code));
            } else {
                String token = state.createOAuthState(uuid, currentIp, name, false);
                firePhaseEnter(uuid, LoginPhase.LOGIN, new LoginCallbacks.PlayerLoginData(false, token));
                state.markPendingLogin(uuid, currentIp, token, false);
            }
            return;
        }

        if (!accounts.isLinked(uuid)) {
            if (bedrock) {
                String code = state.recentBedrockCodeAfterLeave(uuid, Duration.ofSeconds(props.bedrockCodeTimeAfterLeave))
                        .orElseGet(() -> state.createOneTimeCode(uuid, currentIp, name));
                state.recordBedrockCodeShown(uuid, code);
                state.markPendingLogin(uuid, currentIp, code, true);
                firePhaseEnter(uuid, LoginPhase.LOGIN, new LoginCallbacks.PlayerLoginData(true, code));
            } else {
                String token = state.createOAuthState(uuid, currentIp, name, false);
                firePhaseEnter(uuid, LoginPhase.LOGIN, new LoginCallbacks.PlayerLoginData(false, token));
                state.markPendingLogin(uuid, currentIp, token, false);
            }
            return;
        }

        String last = profiles.findLastConfirmedIp(uuid).orElse(null);
        if (!Objects.equals(last, currentIp)) {
            long discordId = accounts.findDiscordForProfile(uuid).orElseThrow();
            state.markPendingIpConfirm(uuid, currentIp, discordId);
            if (dm != null) dm.sendLocationConfirmMessage(discordId, uuid, name, currentIp);
            firePhaseEnter(uuid, LoginPhase.IP_CONFIRM, null);
        }
    }

    public boolean isActionAllowed(UUID uuid, String currentIp) {
        if (!accounts.isLinked(uuid)) return false;
        if (state.isPendingLogin(uuid)) return false;
        if (state.isPendingIpConfirm(uuid)) return false;
        String last = profiles.findLastConfirmedIp(uuid).orElse(null);
        // Simple IP policy: must match last confirmed IP
        return Objects.equals(last, currentIp);
    }

    public String buildDiscordAuthUrl(String stateToken) {
        if (!state.hasOAuthState(stateToken))
            throw new IllegalStateException("Unknown or expired login state");
        return oauth.buildAuthUrl(stateToken, webServerInfo.getPublicCallbackURL());
    }

    public String createOAuthState(UUID uuid, String ip, String name, boolean bedrock) {
        return state.createOAuthState(uuid, ip, name, bedrock);
    }

    public void handleWebServerCallback(String code, String stateToken) {
        var st = state.consumeOAuthState(stateToken);
        var tokenSet = oauth.exchangeCode(code, webServerInfo.getPublicCallbackURL());
        var user = oauth.fetchUser(tokenSet.accessToken());
        Properties props = propertiesProvider.getSnapshot();

        // Ensure Discord user exists before creating FK-dependent records
        if (discordUserRepo != null) {
            discordUserRepo.upsertUser(user.id(), user.username(), user.globalName(), user.email(), user.avatar());
        }

        // If profile is already linked/reserved for another Discord user, block with 403
        var existing = accounts.findAnyDiscordForProfile(st.uuid());
        if (existing.isPresent() && existing.get() != user.id()) {
            throw new ForbiddenLinkException("Profile is reserved for a different Discord account");
        }

        // Enforce per-Discord platform limits unless already linked to same discord
        String platform = st.bedrock() ? "BEDROCK" : "JAVA";
        var cur = accounts.findDiscordForProfile(st.uuid());
        boolean bypass = state.consumeLimitBypass(st.uuid());
        if (!bypass && (cur.isEmpty() || cur.get() != user.id())) {
            int limit = st.bedrock() ? props.bedrockLimitPerDiscord : props.javaLimitPerDiscord;
            if (limit > 0) {
                int count = accounts.countByDiscordAndPlatform(user.id(), platform, props.limitIncludeReserved);
                if (count >= limit) {
                    throw new ForbiddenLinkException("Link limit reached for platform " + platform);
                }
            }
        }

        accounts.activate(user.id(), st.uuid());
        tokens.save(user.id(), tokenSet.accessToken(), tokenSet.refreshToken(), tokenSet.expiresAt(),
                tokenSet.tokenType(), tokenSet.scope());
        if (discordUserRepo != null) {
            boolean hasCommands = tokenSet.scope() != null && tokenSet.scope().contains("applications.commands");
            discordUserRepo.setCommandsInstalled(user.id(), hasCommands);
        }

        profiles.updateLastConfirmedIp(st.uuid(), st.ip());
        profiles.updatePlatform(st.uuid(), st.bedrock() ? "BEDROCK" : "JAVA");

        if (dm != null) dm.sendGreetingsMessage(user.id(), st.uuid(), st.name());
        state.clearPendingLogin(st.uuid());

        firePhaseExit(st.uuid(), LoginPhase.LOGIN, LoginExitReason.LOGIN_SUCCESS);
    }

    public void acceptNewAddress(UUID profileUuid, long discordUserId) {
        Optional<Long> owner = accounts.findDiscordForProfile(profileUuid);
        if (owner.isEmpty() || owner.get() != discordUserId) {
            log.warn("Canceled accept attempt on profile that has no record. (another bot instance is running?) profile={} by {}", profileUuid, discordUserId);
            return;
        }
        var pending = state.consumePendingIpConfirm(profileUuid);
        if (pending == null) return;

        profiles.updateLastConfirmedIp(profileUuid, pending.newIp());
        callbacks.onIpConfirmed(profileUuid, pending.newIp());
        firePhaseExit(profileUuid, LoginPhase.IP_CONFIRM, LoginExitReason.IP_CONFIRM_CONFIRMED);
    }

    public void rejectNewAddress(UUID profileUuid, long discordUserId) {
        Optional<Long> owner = accounts.findDiscordForProfile(profileUuid);
        if (owner.isEmpty() || owner.get() != discordUserId) {
            log.warn("Canceled rejection attempt on profile that has no record. (another bot instance is running?) profile={} by {}", profileUuid, discordUserId);
            return;
        }

        firePhaseExit(profileUuid, LoginPhase.IP_CONFIRM, LoginExitReason.IP_CONFIRM_REJECT);
    }

    public boolean handleBedrockLoginCommand(String code, long discordUserId) {
        var pending = state.consumeOneTimeCode(code);
        if (pending == null) return false;
        Properties props = propertiesProvider.getSnapshot();

        boolean bypass = state.hasLimitBypass(pending.uuid());
        if (!bypass) {
            int limit = props.bedrockLimitPerDiscord;
            if (limit > 0) {
                int count = accounts.countByDiscordAndPlatform(discordUserId, "BEDROCK", props.limitIncludeReserved);
                if (count >= limit) return false;
            }
        }

        // Reserve the link, full activation happens after OAuth
        accounts.reserve(discordUserId, pending.uuid());
        profiles.updateLastConfirmedIp(pending.uuid(), pending.ip());
        profiles.updatePlatform(pending.uuid(), "BEDROCK");

        String token = state.createOAuthState(pending.uuid(), pending.ip(), pending.name(), true);
        if (dm != null) dm.sendOAuth2URLMessage(discordUserId, webServerInfo.displayableUrl());

        return true;
    }

    public void handleLoginTimeout(UUID uuid) {
        state.clearPendingLogin(uuid);
        firePhaseExit(uuid, LoginPhase.LOGIN, LoginExitReason.LOGIN_TIMEOUT);
    }

    public void handleAddressConfirmTimeout(UUID uuid) {
        state.consumePendingIpConfirm(uuid);
        firePhaseExit(uuid, LoginPhase.IP_CONFIRM, LoginExitReason.IP_CONFIRM_TIMEOUT);
    }

    private void firePhaseEnter(UUID uuid, LoginPhase phase, LoginCallbacks.PlayerLoginData data) {
        callbacks.onLoginPhaseEnter(uuid, phase, data);
    }

    private void firePhaseExit(UUID uuid, LoginPhase phase, LoginExitReason cause) {
        callbacks.onLoginPhaseExit(uuid, phase, cause);
    }
}
