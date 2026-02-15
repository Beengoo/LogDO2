package ua.beengoo.logdo2.api.events.bukkit;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import ua.beengoo.logdo2.api.events.LoginExitReason;
import ua.beengoo.logdo2.api.events.LoginPhase;

/**
 * Fires when player logout at any login phase
 * */
@Getter
public class PlayerLoginPhaseExitEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final LoginPhase phase;
    private final @NonNull LoginExitReason loginExitReason;

    public PlayerLoginPhaseExitEvent(@NotNull Player player, @NotNull LoginPhase phase, @NonNull LoginExitReason loginExitReason) {
        this.player = player;
        this.phase = phase;
        this.loginExitReason = loginExitReason;
    }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}

