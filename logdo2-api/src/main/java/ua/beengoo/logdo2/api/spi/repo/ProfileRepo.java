package ua.beengoo.logdo2.api.spi.repo;

import java.util.Optional;
import java.util.UUID;

public interface ProfileRepo {
    Optional<String> findLastConfirmedIp(UUID profileUuid);
    void updateLastConfirmedIp(UUID profileUuid, String ip);
    void upsertName(UUID profileUuid, String playerName);

    void updatePlatform(UUID profileUuid, String platform);
    Optional<UUID> findUuidByName(String name);
    Optional<String> findNameByUuid(UUID uuid);
    Optional<String> findPlatform(UUID uuid);
}
