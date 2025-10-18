package ua.beengoo.logdo2.plugin.adapters.api;

import lombok.Setter;
import net.dv8tion.jda.api.JDA;
import ua.beengoo.logdo2.api.DiscordAccount;
import ua.beengoo.logdo2.api.LogDO2Api;
import ua.beengoo.logdo2.api.MinecraftProfile;
import ua.beengoo.logdo2.api.SessionView;
import ua.beengoo.logdo2.api.ports.AccountsRepo;
import ua.beengoo.logdo2.api.ports.LoginStatePort;
import ua.beengoo.logdo2.api.ports.ProfileRepo;
import ua.beengoo.logdo2.core.service.LoginService;

import java.util.*;
import java.util.stream.Collectors;

public class LogDO2ApiImpl implements LogDO2Api {
    private final LoginService service;
    private final ProfileRepo profiles;
    private final AccountsRepo accounts;
    private final LoginStatePort loginState;
    @Setter
    private JDA discordBot;
    private final String targetGuildId;

    public LogDO2ApiImpl(LoginService service,
                         ProfileRepo profiles,
                         AccountsRepo accounts,
                         LoginStatePort loginState,
                         JDA discordBot,
                         String targetGuildId) {
        this.service = service;
        this.profiles = profiles;
        this.accounts = accounts;
        this.loginState = loginState;
        this.discordBot = discordBot;
        this.targetGuildId = targetGuildId;
    }

    @Override
    public boolean isLinked(UUID uuid) {
        return accounts.isLinked(uuid);
    }

    @Override
    public Optional<Long> discordId(UUID uuid) {
        return accounts.findDiscordForProfile(uuid);
    }

    @Override
    public Set<UUID> getProfiles(Long discordId) {
        return accounts.findProfilesForDiscord(discordId);
    }

    @Override
    public Optional<String> lastConfirmedIp(UUID uuid) {
        return profiles.findLastConfirmedIp(uuid);
    }

    @Override
    public boolean isActionAllowed(UUID uuid, String currentIp) {
        return service.isActionAllowed(uuid, currentIp);
    }

    @Override
    public DiscordAccount getDiscordAccount(long discordId) {
        // keep order: accounts.findProfilesForDiscord returns a Set — if you need order, ensure repo uses LinkedHashSet
        List<DiscordAccount.MinecraftProfileSummary> summaries = accounts.findProfilesForDiscord(discordId).stream()
                .map(uuid -> {
                    String name = profiles.findNameByUuid(uuid).orElse(null);
                    MinecraftProfile.MinecraftPlatform platform = profiles.findPlatform(uuid)
                            .map(MinecraftProfile.MinecraftPlatform::fromDatabase)
                            .orElse(MinecraftProfile.MinecraftPlatform.UNKNOWN);
                    boolean primary = accounts.findDiscordForProfile(uuid).map(d -> d == discordId).orElse(false);
                    return new DiscordAccount.MinecraftProfileSummary(uuid, name, platform, primary);
                })
                .collect(Collectors.toList());
        return new DiscordAccount(discordId, summaries);
    }

    @Override
    public MinecraftProfile getMinecraftProfile(UUID uuid) {
        String name = profiles.findNameByUuid(uuid).orElse(null);
        MinecraftProfile.MinecraftPlatform platform = profiles.findPlatform(uuid)
                .map(MinecraftProfile.MinecraftPlatform::fromDatabase)
                .orElse(MinecraftProfile.MinecraftPlatform.UNKNOWN);
        String lastIp = profiles.findLastConfirmedIp(uuid).orElse(null);
        Optional<Long> linkedDiscord = accounts.findDiscordForProfile(uuid);
        return new MinecraftProfile(uuid, name, platform, lastIp, linkedDiscord);
    }

    public SessionView getSessionForProfile(UUID uuid) {
        Optional<SessionView.PendingLoginView> pendingLogin = loginState.listPendingLogins().stream()
                .filter(pl -> pl.uuid().equals(uuid))
                .findFirst()
                .map(pl -> new SessionView.PendingLoginView(pl.ip(), pl.bedrock(), pl.at()));

        Optional<SessionView.PendingIpConfirmView> pendingIp = loginState.listPendingIpConfirms().stream()
                .filter(pi -> pi.uuid().equals(uuid))
                .findFirst()
                .map(pi -> new SessionView.PendingIpConfirmView(pi.newIp(), pi.discordId(), pi.at()));

        boolean bypass = loginState.hasLimitBypass(uuid);
        return new SessionView(pendingLogin, pendingIp, bypass);
    }

    public List<MinecraftProfile> getUsersByDiscord(long discordId) {
        List<MinecraftProfile> list = new ArrayList<>();
        for (UUID uuid : accounts.findProfilesForDiscord(discordId)) {
            list.add(getMinecraftProfile(uuid));
        }
        return List.copyOf(list);
    }

    @Override
    public JDA getDiscordBot() {
        return discordBot;
    }

    @Override
    public String getTargetGuildId() {
        return targetGuildId;
    }
}
