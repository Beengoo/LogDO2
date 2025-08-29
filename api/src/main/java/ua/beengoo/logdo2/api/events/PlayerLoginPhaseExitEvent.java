package ua.beengoo.logdo2.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PlayerLoginPhaseExitEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final LoginPhase phase;

    public PlayerLoginPhaseExitEvent(@NotNull Player player, @NotNull LoginPhase phase) {
        this.player = player;
        this.phase = phase;
    }

    public Player getPlayer() { return player; }
    public LoginPhase getPhase() { return phase; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}

