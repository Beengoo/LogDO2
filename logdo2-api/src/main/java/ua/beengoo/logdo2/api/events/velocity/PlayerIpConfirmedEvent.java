package ua.beengoo.logdo2.api.events.velocity;

import com.velocitypowered.api.proxy.Player;
import lombok.Getter;

/**
 * Fires when player passes IP confirm stage
 */
@Getter
public class PlayerIpConfirmedEvent {

    private final Player player;
    private final String confirmedIp;

    public PlayerIpConfirmedEvent(Player player, String confirmedIp) {
        this.player = player;
        this.confirmedIp = confirmedIp;
    }
}