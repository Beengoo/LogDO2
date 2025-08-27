package ua.beengoo.logdo2.plugin.runtime;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import ua.beengoo.logdo2.api.ports.LoginStatePort;
import ua.beengoo.logdo2.core.service.LoginService;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class TimeoutManager {
    private final Plugin plugin;
    private final LoginStatePort state;
    private final LoginService service;
    private final Duration loginTtl;
    private final Duration ipTtl;
    private int taskId = -1;

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

        // First login timeouts
        for (LoginStatePort.PendingLogin p : state.listPendingLogins()) {
            if (Duration.between(p.at(), now).compareTo(loginTtl) > 0) {
                UUID uuid = p.uuid();
                service.onLoginTimeout(uuid);
            }
        }

        // IP confirm timeouts
        for (LoginStatePort.PendingIp p : state.listPendingIpConfirms()) {
            if (Duration.between(p.at(), now).compareTo(ipTtl) > 0) {
                service.onIpConfirmTimeout(p.uuid());
            }
        }
    }
}
