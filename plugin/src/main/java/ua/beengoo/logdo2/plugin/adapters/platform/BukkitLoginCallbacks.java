package ua.beengoo.logdo2.plugin.adapters.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ua.beengoo.logdo2.api.events.*;
import ua.beengoo.logdo2.api.spi.callbacks.LoginCallbacks;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Bukkit implementation of LoginCallbacks.
 * Converts core module callbacks into Bukkit events.
 * Handles thread-safety for Folia compatibility.
 */
public class BukkitLoginCallbacks implements LoginCallbacks {

    private final Plugin plugin;

    public BukkitLoginCallbacks(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onLoginPhaseEnter(UUID uuid, LoginPhase phase, PlayerLoginData data) {
        // Convert to Bukkit event data
        PlayerLoginPhaseEnterEvent.PlayerLoginData eventData = data != null
                ? new PlayerLoginPhaseEnterEvent.PlayerLoginData(data.bedrock(), data.token())
                : null;

        runPlayer(uuid, player -> {
            try {
                Bukkit.getPluginManager().callEvent(new PlayerLoginPhaseEnterEvent(player, phase, eventData));
            } catch (Throwable ignored) {
                // Silently ignore event handling errors
            }
        });
    }

    @Override
    public void onLoginPhaseExit(UUID uuid, LoginPhase phase, LoginExitReason reason) {
        runPlayer(uuid, player -> {
            try {
                Bukkit.getPluginManager().callEvent(new PlayerLoginPhaseExitEvent(player, phase, reason));
            } catch (Throwable ignored) {
                // Silently ignore event handling errors
            }
        });
    }

    @Override
    public void onIpConfirmed(UUID uuid, String confirmedIp) {
        runPlayer(uuid, player -> {
            try {
                Bukkit.getPluginManager().callEvent(new PlayerIpConfirmedEvent(player, confirmedIp));
            } catch (Throwable ignored) {}
        });
    }

    /**
     * Thread-safe player action executor.
     * Ensures events are fired on the correct thread for Folia compatibility.
     */
    private void runPlayer(UUID uuid, Consumer<Player> action) {
        if (Bukkit.isPrimaryThread()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) action.accept(p);
            return;
        }
        try {
            // Folia: execute on global scheduler then switch to player scheduler
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    try {
                        p.getScheduler().execute(plugin, () -> action.accept(p), null, 0L);
                    } catch (Throwable ignored) {
                        // Fallback for non-Folia Paper
                        action.accept(p);
                    }
                }
            });
        } catch (Throwable ignored) {
            // Fallback for legacy Bukkit
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) action.accept(p);
            });
        }
    }
}
