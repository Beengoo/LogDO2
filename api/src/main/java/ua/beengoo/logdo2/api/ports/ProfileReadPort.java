package ua.beengoo.logdo2.api.ports;

import java.util.Optional;
import java.util.UUID;

public interface ProfileReadPort {
    /** Повертає останню підтверджену IP для UUID гравця, якщо є. */
    Optional<String> findLastConfirmedIp(UUID playerUuid);
}
