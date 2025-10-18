package ua.beengoo.logdo2.api.events;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class PlayerIpConfirmedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String confirmedIp;

    public PlayerIpConfirmedEvent(@NotNull Player player, @NotNull String confirmedIp) {
        this.player = player;
        this.confirmedIp = confirmedIp;
    }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
