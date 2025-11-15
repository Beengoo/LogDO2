package ua.beengoo.logdo2.api.entity;

/**
 * {@code UNAUTHORIZED 0} - When profile not authorized, authorization tokens was expired and no way to refresh or entry added without player authorization.
 * <p>
 * {@code AUTHORIZED 1} - When profile exists and authorized
 * <p>
 * {@code BLOCKED 2} - When profile got banned
 */
public enum LogDO2ProfileStatus {
    UNAUTHORIZED(0),
    AUTHORIZED(1),
    BLOCKED(2);

    LogDO2ProfileStatus(int i) {

    }
}
