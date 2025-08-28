package ua.beengoo.logdo2.plugin.runtime;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import ua.beengoo.logdo2.api.ports.LoginStatePort;
import ua.beengoo.logdo2.core.service.LoginService;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TimeoutManager {
    private final Plugin plugin;
    private final LoginStatePort state;
    private final LoginService service;
    private final Duration loginTtl;
    private final Duration ipTtl;
    private int taskId = -1;
    private final Map<UUID, Long> lastLoginTitle = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastIpTitle = new ConcurrentHashMap<>();

    public TimeoutManager(Plugin plugin, LoginStatePort state, LoginService service,
                          Duration loginTtl, Duration ipTtl) {
        this.plugin = plugin;
        this.state = state;
        this.service = service;
        this.loginTtl = loginTtl;
        this.ipTtl = ipTtl;
    }

    public void start() {
        // раз на секунду перевіряємо таймаути
        this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void tick() {
        Instant now = Instant.now();
        long nowSec = now.getEpochSecond();

        // First login timeouts
        for (LoginStatePort.PendingLogin p : state.listPendingLogins()) {
            if (Duration.between(p.at(), now).compareTo(loginTtl) > 0) {
                UUID uuid = p.uuid();
                service.onLoginTimeout(uuid);
            }
            // Refresh title every ~5 seconds with 0 fade to hold it on screen
            Long last = lastLoginTitle.get(p.uuid());
            if (last == null || nowSec - last >= 5) {
                service.showLoginPhaseTitle(p.uuid());
                lastLoginTitle.put(p.uuid(), nowSec);
            }
        }

        // IP confirm timeouts
        for (LoginStatePort.PendingIp p : state.listPendingIpConfirms()) {
            if (Duration.between(p.at(), now).compareTo(ipTtl) > 0) {
                service.onIpConfirmTimeout(p.uuid());
            }
            Long last = lastIpTitle.get(p.uuid());
            if (last == null || nowSec - last >= 5) {
                service.showIpConfirmPhaseTitle(p.uuid());
                lastIpTitle.put(p.uuid(), nowSec);
            }
        }

        // Cleanup stale entries (no longer pending)
        lastLoginTitle.keySet().removeIf(uuid -> state.listPendingLogins().stream().noneMatch(p -> p.uuid().equals(uuid)));
        lastIpTitle.keySet().removeIf(uuid -> state.listPendingIpConfirms().stream().noneMatch(p -> p.uuid().equals(uuid)));
    }
}
