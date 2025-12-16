package ua.beengoo.logdo2.api.service;

import ua.beengoo.logdo2.api.SessionView;

import java.util.UUID;

/**
 * Session-related queries (pending login, ip confirm, bypass checks).
 */
public interface SessionService {
    SessionView getSessionForProfile(UUID uuid);
    boolean isActionAllowed(UUID uuid, String currentIp);
}
