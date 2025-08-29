package ua.beengoo.logdo2.plugin.adapters.api;

import ua.beengoo.logdo2.api.ports.ProfileReadPort;
import ua.beengoo.logdo2.api.ports.ProfileRepo;

import java.util.Optional;
import java.util.UUID;

public class ProfileReadAdapter implements ProfileReadPort {
    private final ProfileRepo delegate;

    public ProfileReadAdapter(ProfileRepo delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<String> findLastConfirmedIp(UUID playerUuid) {
        return delegate.findLastConfirmedIp(playerUuid);
    }
}

