package ua.beengoo.logdo2.plugin.runtime;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ua.beengoo.logdo2.api.ports.LoginStatePort;
import ua.beengoo.logdo2.core.service.LoginService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TimeoutManager {
    private final Plugin plugin;
    private final LoginStatePort state;
    private final LoginService service;
    private final Duration loginTtl;
    private final Duration ipTtl;

    // Scheduling state
    private boolean folia; // true if Folia APIs are available
    private int legacyTaskId = -1; // Spigot/Paper task id
    private Object foliaTask = null; // io.papermc.paper.threadedregions.scheduler.ScheduledTask, but kept as Object to avoid hard dep

    // Throttle for titles
    private final Map<UUID, Long> lastLoginTitle = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastIpTitle = new ConcurrentHashMap<>();

    public TimeoutManager(Plugin plugin, LoginStatePort state, LoginService service,
                          Duration loginTtl, Duration ipTtl) {
        this.plugin = plugin;
        this.state = state;
        this.service = service;
        this.loginTtl = loginTtl;
        this.ipTtl = ipTtl;
        this.folia = detectFolia();
    }

    public void start() {
        // 20L delay/period = 1s
        if (folia) {
            // Folia: run periodic task on Global Region thread
            foliaTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                    plugin,
                    st -> tick(), // DO NOT touch entities/world directly inside tick without re-scheduling to entity
                    20L,
                    20L
            );
        } else {
            // Spigot/Paper legacy scheduler on main thread
            legacyTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 20L, 20L);
        }
    }

    public void stop() {
        if (folia) {
            if (foliaTask != null) {
                // Avoid compile-time Folia import; reflectively cast
                try {
                    Class<?> cls = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
                    cls.getMethod("cancel").invoke(foliaTask);
                } catch (Exception ignored) {
                    // If this fails, there is nothing else to do
                } finally {
                    foliaTask = null;
                }
            }
        } else {
            if (legacyTaskId != -1) {
                Bukkit.getScheduler().cancelTask(legacyTaskId);
                legacyTaskId = -1;
            }
        }
    }

    private void tick() {
        final Instant now = Instant.now();
        final long nowSec = now.getEpochSecond();

        // Snapshot pending states once per tick to avoid repeated calls
        final var pendingLogins = state.listPendingLogins();
        final var pendingIps = state.listPendingIpConfirms();

        // First login timeouts + titles
        for (LoginStatePort.PendingLogin p : pendingLogins) {
            final UUID uuid = p.uuid();

            if (Duration.between(p.at(), now).compareTo(loginTtl) > 0) {
                runOnPlayerThread(uuid, () -> service.onLoginTimeout(uuid));
            }

            Long last = lastLoginTitle.get(uuid);
            if (last == null || nowSec - last >= 5) {
                runOnPlayerThread(uuid, () -> service.showLoginPhaseTitle(uuid));
                lastLoginTitle.put(uuid, nowSec);
            }
        }

        // IP confirm timeouts + titles
        for (LoginStatePort.PendingIp p : pendingIps) {
            final UUID uuid = p.uuid();

            if (Duration.between(p.at(), now).compareTo(ipTtl) > 0) {
                runOnPlayerThread(uuid, () -> service.onIpConfirmTimeout(uuid));
            }

            Long last = lastIpTitle.get(uuid);
            if (last == null || nowSec - last >= 5) {
                runOnPlayerThread(uuid, () -> service.showIpConfirmPhaseTitle(uuid));
                lastIpTitle.put(uuid, nowSec);
            }
        }

        // Cleanup stale entries (no longer pending)
        final Set<UUID> stillPendingLogin = pendingLogins.stream().map(LoginStatePort.PendingLogin::uuid).collect(java.util.stream.Collectors.toSet());
        final Set<UUID> stillPendingIp = pendingIps.stream().map(LoginStatePort.PendingIp::uuid).collect(java.util.stream.Collectors.toSet());
        lastLoginTitle.keySet().removeIf(uuid -> !stillPendingLogin.contains(uuid));
        lastIpTitle.keySet().removeIf(uuid -> !stillPendingIp.contains(uuid));
    }

    /**
     * Ensure player-affecting code runs on the correct thread.
     * On Folia: use EntityScheduler of the Player (if online).
     * On legacy: we are already on the main thread due to scheduleSyncRepeatingTask.
     */
    private void runOnPlayerThread(UUID uuid, Runnable action) {
        if (!folia) {
            // Main thread already
            action.run();
            return;
        }

        // Folia path: re-schedule onto the player's entity thread
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return; // Player offline; nothing to do

        // player.getScheduler().run(plugin, task -> action.run(), null) — no delay
        player.getScheduler().run(plugin, scheduledTask -> {
            try {
                action.run();
            } catch (Throwable t) {
                // Avoid crashing scheduler due to plugin exception
                plugin.getLogger().warning("TimeoutManager action failed for " + uuid + ": " + t.getMessage());
            }
        }, null);
    }

    private boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
