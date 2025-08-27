package ua.beengoo.logdo2.api.ports;

import java.util.Optional;

public interface BanProgressRepo {
    Optional<Record> findByIp(String ip);
    void upsert(String ip, int attempts, long lastAttemptEpochSec, long lastBanUntilEpochSec);
    void reset(String ip);

    record Record(String ip, int attempts, long lastAttemptEpochSec, long lastBanUntilEpochSec) {}
}
