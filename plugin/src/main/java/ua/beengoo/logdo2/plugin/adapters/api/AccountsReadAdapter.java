package ua.beengoo.logdo2.plugin.adapters.api;

import ua.beengoo.logdo2.api.ports.AccountsReadPort;
import ua.beengoo.logdo2.api.ports.AccountsRepo;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class AccountsReadAdapter implements AccountsReadPort {
    private final AccountsRepo delegate;

    public AccountsReadAdapter(AccountsRepo delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean isLinked(UUID profileUuid) {
        return delegate.isLinked(profileUuid);
    }

    @Override
    public Optional<Long> findDiscordForProfile(UUID profileUuid) {
        return delegate.findDiscordForProfile(profileUuid);
    }

    @Override
    public Set<UUID> findProfilesForDiscord(long discordId) {
        return delegate.findProfilesForDiscord(discordId);
    }
}

