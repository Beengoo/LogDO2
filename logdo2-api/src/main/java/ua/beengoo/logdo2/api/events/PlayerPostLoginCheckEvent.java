package ua.beengoo.logdo2.api.events;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after login gating is evaluated for a player.
 */
@Getter
public class PlayerPostLoginCheckEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String ip;
    private final boolean bedrock;
    private final boolean allowed;
    private final String reason; // nullable string, present when not allowed

    public PlayerPostLoginCheckEvent(@NotNull Player player, @NotNull String ip,
                                     boolean bedrock, boolean allowed, String reason) {
        this.player = player;
        this.ip = ip;
        this.bedrock = bedrock;
        this.allowed = allowed;
        this.reason = reason;
    }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}

