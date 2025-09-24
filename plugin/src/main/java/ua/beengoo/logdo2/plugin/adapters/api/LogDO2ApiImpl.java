package ua.beengoo.logdo2.plugin.adapters.api;

import net.dv8tion.jda.api.JDA;
import ua.beengoo.logdo2.api.LogDO2Api;
import ua.beengoo.logdo2.api.ports.AccountsRepo;
import ua.beengoo.logdo2.api.ports.ProfileRepo;
import ua.beengoo.logdo2.core.service.LoginService;

import java.util.Optional;
import java.util.UUID;

public class LogDO2ApiImpl implements LogDO2Api {
    private final LoginService service;
    private final ProfileRepo profiles;
    private final AccountsRepo accounts;
    private final JDA discordBot;

    public LogDO2ApiImpl(LoginService service, ProfileRepo profiles, AccountsRepo accounts, JDA discordBot) {
        this.service = service;
        this.profiles = profiles;
        this.accounts = accounts;
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
    public Optional<String> lastConfirmedIp(UUID uuid) {
        return profiles.findLastConfirmedIp(uuid);
    }

    @Override
    public boolean isActionAllowed(UUID uuid, String currentIp) {
        return service.isActionAllowed(uuid, currentIp);
    }

    @Override
    public JDA getDiscordBot() {
        return discordBot;
    }
}

