package ua.beengoo.logdo2.plugin.adapters.api;

import net.dv8tion.jda.api.JDA;
import ua.beengoo.logdo2.api.LogDO2Api;
import ua.beengoo.logdo2.api.LogDO2User;
import ua.beengoo.logdo2.api.ports.AccountsRepo;
import ua.beengoo.logdo2.api.ports.LoginStatePort;
import ua.beengoo.logdo2.api.ports.ProfileRepo;
import ua.beengoo.logdo2.core.service.LoginService;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class LogDO2ApiImpl implements LogDO2Api {
    private final LoginService service;
    private final ProfileRepo profiles;
    private final AccountsRepo accounts;
    private final LoginStatePort loginState;
    private final JDA discordBot;

    public LogDO2ApiImpl(LoginService service,
                         ProfileRepo profiles,
                         AccountsRepo accounts,
                         LoginStatePort loginState,
                         JDA discordBot) {
        this.service = service;
        this.profiles = profiles;
        this.accounts = accounts;
        this.loginState = loginState;
        this.discordBot = discordBot;
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
    public LogDO2User getUser(UUID uuid) {
        var primary = buildProfile(uuid);
        var activeDiscord = accounts.findDiscordForProfile(uuid);
        var anyDiscord = accounts.findAnyDiscordForProfile(uuid);

        var profilesForDiscord = new LinkedHashSet<LogDO2User.LinkedProfile>();
        profilesForDiscord.add(new LogDO2User.LinkedProfile(primary, true));

        activeDiscord.ifPresent(discordId -> {
            for (UUID linkedUuid : accounts.findProfilesForDiscord(discordId)) {
                if (linkedUuid.equals(uuid)) continue;
                var profile = buildProfile(linkedUuid);
                profilesForDiscord.add(new LogDO2User.LinkedProfile(profile, false));
            }
        });

        var pendingLogin = loginState.listPendingLogins().stream()
                .filter(pl -> pl.uuid().equals(uuid))
                .findFirst()
                .map(pl -> new LogDO2User.PendingLogin(pl.ip(), pl.bedrock(), pl.at()));

        var pendingIp = loginState.listPendingIpConfirms().stream()
                .filter(pi -> pi.uuid().equals(uuid))
                .findFirst()
                .map(pi -> new LogDO2User.PendingIpConfirm(pi.newIp(), pi.discordId(), pi.at()));

        boolean bypass = loginState.hasLimitBypass(uuid);

        var session = new LogDO2User.Session(pendingLogin, pendingIp, bypass);
        var discord = new LogDO2User.DiscordLink(activeDiscord, anyDiscord, profilesForDiscord);

        return new LogDO2User(primary, discord, session);
    }

    @Override
    public JDA getDiscordBot() {
        return discordBot;
    }

    private LogDO2User.MinecraftProfile buildProfile(UUID uuid) {
        var name = profiles.findNameByUuid(uuid).orElse(null);
        var platform = profiles.findPlatform(uuid)
                .map(LogDO2User.MinecraftPlatform::fromDatabase)
                .orElse(LogDO2User.MinecraftPlatform.UNKNOWN);
        var lastIp = profiles.findLastConfirmedIp(uuid).orElse(null);
        return new LogDO2User.MinecraftProfile(uuid, name, platform, lastIp);
    }
}
