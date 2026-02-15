package ua.beengoo.logdo2.api.events.velocity;

import com.velocitypowered.api.proxy.Player;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/**
 * Fired after login gating is evaluated for a player.
 */
@Getter
public class PlayerPostLoginCheckEvent {

    private final Player player;
    private final String ip;
    private final boolean bedrock;
    private final boolean allowed;
    private final @Nullable String reason; // present when not allowed

    public PlayerPostLoginCheckEvent(Player player, String ip, boolean bedrock,
                                     boolean allowed, @Nullable String reason) {
        this.player = player;
        this.ip = ip;
        this.bedrock = bedrock;
        this.allowed = allowed;
        this.reason = reason;
    }
}