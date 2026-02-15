package ua.beengoo.logdo2.api.events.velocity;

import com.velocitypowered.api.proxy.Player;
import lombok.Getter;
import ua.beengoo.logdo2.api.events.LoginPhase;

/**
 * Fires when any Login phase was begun
 */
@Getter
public class PlayerLoginPhaseEnterEvent {

    public record PlayerLoginData(boolean bedrock, String token) {}

    private final Player player;
    private final LoginPhase phase;
    private final PlayerLoginData data;

    public PlayerLoginPhaseEnterEvent(Player player, LoginPhase phase, PlayerLoginData data) {
        this.player = player;
        this.phase = phase;
        this.data = data;
    }
}