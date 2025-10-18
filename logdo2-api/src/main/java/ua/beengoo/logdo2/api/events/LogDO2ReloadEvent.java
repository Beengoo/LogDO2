package ua.beengoo.logdo2.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when plugin reloaded
 */
public class LogDO2ReloadEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();


    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}
