package ua.beengoo.logdo2.api.events;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;


@Getter
public class PlayerIpCheckEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String currentIp;
    private boolean allowed;

    public PlayerIpCheckEvent(@NotNull Player player, @NotNull String currentIp, boolean defaultAllowed) {
        this.player = player;
        this.currentIp = currentIp;
        this.allowed = defaultAllowed;
    }

    public void setAllowed(boolean allowed) { this.allowed = allowed; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
