package ua.beengoo.logdo2.api.events;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fires when player logout at any login phase
 * */
@Getter
public class PlayerLoginPhaseExitEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final LoginPhase phase;

    public PlayerLoginPhaseExitEvent(@NotNull Player player, @NotNull LoginPhase phase) {
        this.player = player;
        this.phase = phase;
    }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}

