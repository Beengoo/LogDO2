package ua.beengoo.logdo2.plugin.adapters.api;

import lombok.Setter;
import net.dv8tion.jda.api.JDA;
import org.bukkit.OfflinePlayer;
import ua.beengoo.logdo2.api.DiscordAccount;
import ua.beengoo.logdo2.api.LogDO2Api;
import ua.beengoo.logdo2.api.MinecraftProfile;
import ua.beengoo.logdo2.api.SessionView;
import ua.beengoo.logdo2.api.entity.DiscordProfile;
import ua.beengoo.logdo2.api.entity.LogDO2Profile;
import ua.beengoo.logdo2.api.service.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Adapter that maps the legacy `LogDO2Api` into the new modular service interfaces.
 * This wrapper allows a gradual migration: other plugins can depend on the new
 * `*Service` interfaces while the plugin still provides a `LogDO2Api` implementation.
 */
public class LogDO2ApiAdapter implements ProfilesService, AccountsService, SessionService, DiscordService, OAuthService {
    private final LogDO2Api delegate;

    public LogDO2ApiAdapter(LogDO2Api delegate) {
        this.delegate = delegate;
    }

    // ProfilesService
    @Override
    public LogDO2Profile getProfile(Long discordId) {
        return delegate.getProfile(discordId);
    }

    @Override
    public LogDO2Profile getProfile(UUID minecraftUUID) {
        return delegate.getProfile(minecraftUUID);
    }

    @Override
    public LogDO2Profile getProfile(OfflinePlayer player) {
        return delegate.getProfile(player);
    }

    // AccountsService
    @Override
    public boolean isLinked(UUID profileUuid) {
        return delegate.isLinked(profileUuid);
    }

    @Override
    public Optional<DiscordProfile> findDiscordForProfile(UUID profileUuid) {
        return Optional.empty();
    }

    @Override
    public Set<UUID> findProfilesForDiscord(long discordId) {
        return delegate.getProfiles(discordId);
    }

    @Override
    public DiscordAccount getDiscordAccount(long discordId) {
        return delegate.getDiscordAccount(discordId);
    }

    @Override
    public List<MinecraftProfile> getUsersByDiscord(long discordId) {
        return delegate.getUsersByDiscord(discordId);
    }

    // SessionService
    @Override
    public SessionView getSessionForProfile(UUID uuid) {
        return delegate.getSessionForProfile(uuid);
    }

    @Override
    public boolean isActionAllowed(UUID uuid, String currentIp) {
        return delegate.isActionAllowed(uuid, currentIp);
    }

    // DiscordService
    @Override
    public JDA getDiscordBot() {
        return delegate.getDiscordBot();
    }

    @Override
    public String getTargetGuildId() {
        return delegate.getTargetGuildId();
    }

    // OAuthService
    @Override
    public ua.beengoo.logdo2.api.ports.OAuthPort.TokenSet exchangeCode(String code, String redirectUri) {
        // forward to the low-level OAuth port if available through delegate implementations
        // by default try to cast delegate to something exposing OAuth (not always available).
        try {
            // many implementations will have access to OAuthPort; this is a best-effort bridge
            var oauthProvider = (ua.beengoo.logdo2.api.ports.OAuthPort) delegate;
            return oauthProvider.exchangeCode(code, redirectUri);
        } catch (ClassCastException ex) {
            throw new UnsupportedOperationException("Delegate does not expose OAuthPort operations");
        }
    }

    @Override
    public String buildAuthUrl(String state, String redirectUri) {
        try {
            var oauthProvider = (ua.beengoo.logdo2.api.ports.OAuthPort) delegate;
            return oauthProvider.buildAuthUrl(state, redirectUri);
        } catch (ClassCastException ex) {
            throw new UnsupportedOperationException("Delegate does not expose OAuthPort operations");
        }
    }
}
