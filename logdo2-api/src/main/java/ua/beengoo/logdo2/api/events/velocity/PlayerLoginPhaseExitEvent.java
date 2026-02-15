package ua.beengoo.logdo2.api.events.velocity;

import com.velocitypowered.api.proxy.Player;
import lombok.Getter;
import lombok.NonNull;
import ua.beengoo.logdo2.api.events.LoginExitReason;
import ua.beengoo.logdo2.api.events.LoginPhase;

/**
 * Fires when player logout at any login phase
 */
@Getter
public class PlayerLoginPhaseExitEvent {

    private final Player player;
    private final LoginPhase phase;
    private final @NonNull LoginExitReason loginExitReason;

    public PlayerLoginPhaseExitEvent(Player player, LoginPhase phase, @NonNull LoginExitReason loginExitReason) {
        this.player = player;
        this.phase = phase;
        this.loginExitReason = loginExitReason;
    }
}