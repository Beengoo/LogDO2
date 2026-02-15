package ua.beengoo.logdo2.api.events.velocity;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.proxy.Player;
import lombok.Getter;

/**
 * Fires when player goes through IP check stage.
 * Allows to skip IP check stage if result is modified.
 */
@Getter
public class PlayerIpCheckEvent implements ResultedEvent<PlayerIpCheckEvent.IpCheckResult> {

    private final Player player;
    private final String currentIp;
    private IpCheckResult result;

    public PlayerIpCheckEvent(Player player, String currentIp, boolean defaultAllowed) {
        this.player = player;
        this.currentIp = currentIp;
        this.result = defaultAllowed ? IpCheckResult.allowed() : IpCheckResult.denied();
    }

    @Override
    public IpCheckResult getResult() {
        return result;
    }

    @Override
    public void setResult(IpCheckResult result) {
        this.result = result;
    }

    public boolean isAllowed() {
        return result.isAllowed();
    }

    public void setAllowed(boolean allowed) {
        this.result = allowed ? IpCheckResult.allowed() : IpCheckResult.denied();
    }

    public static class IpCheckResult implements Result {
        private final boolean allowed;

        private IpCheckResult(boolean allowed) {
            this.allowed = allowed;
        }

        public static IpCheckResult allowed() {
            return new IpCheckResult(true);
        }

        public static IpCheckResult denied() {
            return new IpCheckResult(false);
        }

        @Override
        public boolean isAllowed() {
            return allowed;
        }
    }
}