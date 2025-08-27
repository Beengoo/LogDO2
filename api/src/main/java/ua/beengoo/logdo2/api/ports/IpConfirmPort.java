package ua.beengoo.logdo2.api.ports;

import java.util.UUID;

public interface IpConfirmPort {
    /** Оновити останню підтверджену IP профілю. */
    void confirmIp(UUID playerUuid, String ip);
}
