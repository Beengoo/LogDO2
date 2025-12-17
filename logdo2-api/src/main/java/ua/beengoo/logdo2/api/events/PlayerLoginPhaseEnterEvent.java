package ua.beengoo.logdo2.api.events;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fires when any Login phase was begin
 * */
@Getter
public class PlayerLoginPhaseEnterEvent extends Event {
    public record PlayerLoginData(boolean bedrock, String token){}

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final LoginPhase phase;
    private final PlayerLoginData data;

    public PlayerLoginPhaseEnterEvent(@NotNull Player player, @NotNull LoginPhase phase, PlayerLoginData data) {
        this.player = player;
        this.phase = phase;
        this.data = data;
    }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}

