package ua.beengoo.logdo2.plugin.adapters.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ua.beengoo.logdo2.api.spi.PlatformBridge;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Bukkit/Spigot implementation of PlatformBridge.
 * Handles Bukkit-specific operations like player lookups and thread scheduling.
 * Supports both legacy Bukkit and Folia (per-player scheduling).
 */
public class BukkitPlatformBridge implements PlatformBridge {
    private final Plugin plugin;

    public BukkitPlatformBridge(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<String> getPlayerNameByUuid(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null ? Optional.of(player.getName()) : Optional.empty();
    }

    @Override
    public boolean isPlayerOnline(UUID uuid) {
        return Bukkit.getPlayer(uuid) != null;
    }

    @Override
    public void executeOnMainThread(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            try {
                // Try Folia's GlobalRegionScheduler first
                Bukkit.getGlobalRegionScheduler().execute(plugin, task);
            } catch (Throwable ignored) {
                // Fall back to legacy Bukkit scheduler
                Bukkit.getScheduler().runTask(plugin, task);
            }
        }
    }

    @Override
    public void executeOnPlayerThread(UUID playerUuid, Consumer<Object> playerAction) {
        if (Bukkit.isPrimaryThread()) {
            Player p = Bukkit.getPlayer(playerUuid);
            if (p != null) playerAction.accept(p);
            return;
        }

        try {
            // Try Folia's per-player scheduling
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
                Player p = Bukkit.getPlayer(playerUuid);
                if (p != null) {
                    try {
                        p.getScheduler().execute(plugin, () -> playerAction.accept(p), null, 0L);
                    } catch (Throwable ignored) {
                        // If player scheduler fails, execute directly
                        playerAction.accept(p);
                    }
                }
            });
        } catch (Throwable ignored) {
            // Fall back to legacy Bukkit scheduler
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(playerUuid);
                if (p != null) playerAction.accept(p);
            });
        }
    }

    @Override
    public boolean isPrimaryThread() {
        return Bukkit.isPrimaryThread();
    }
}
