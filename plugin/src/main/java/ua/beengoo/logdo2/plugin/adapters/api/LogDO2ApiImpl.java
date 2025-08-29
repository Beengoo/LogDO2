package ua.beengoo.logdo2.plugin.adapters.api;

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

    public LogDO2ApiImpl(LoginService service, ProfileRepo profiles, AccountsRepo accounts) {
        this.service = service;
        this.profiles = profiles;
        this.accounts = accounts;
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
}

