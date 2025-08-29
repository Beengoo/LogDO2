package ua.beengoo.logdo2.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after login gating is evaluated for a player.
 * Notify-only: listeners must not change the result.
 */
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

    public Player getPlayer() { return player; }
    public String getIp() { return ip; }
    public boolean isBedrock() { return bedrock; }
    public boolean isAllowed() { return allowed; }
    public String getReason() { return reason; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}

