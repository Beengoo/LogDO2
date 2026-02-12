package ua.beengoo.logdo2.api.spi.callbacks;

import ua.beengoo.logdo2.api.events.LoginExitReason;
import ua.beengoo.logdo2.api.events.LoginPhase;

import java.util.UUID;

/**
 * Callback interface for login-related events.
 * Core module uses this interface to notify the plugin layer about login state changes
 * without depending on Bukkit event system.
 */
public interface LoginCallbacks {

    /**
     * Data associated with a login phase entry
     * @param bedrock Whether this is a Bedrock player
     * @param token The OAuth token or one-time code for this login attempt
     */
    record PlayerLoginData(boolean bedrock, String token) {}

    /**
     * Called when a player enters a new login phase
     * @param uuid The player's UUID
     * @param phase The login phase being entered
     * @param data Additional data about the login phase
     */
    void onLoginPhaseEnter(UUID uuid, LoginPhase phase, PlayerLoginData data);

    /**
     * Called when a player exits a login phase
     * @param uuid The player's UUID
     * @param phase The login phase being exited
     * @param reason The reason for exiting the phase
     */
    void onLoginPhaseExit(UUID uuid, LoginPhase phase, LoginExitReason reason);

    /**
     * Called when a player's IP address has been confirmed
     * @param uuid The player's UUID
     * @param confirmedIp The IP address that was confirmed
     */
    void onIpConfirmed(UUID uuid, String confirmedIp);
}
