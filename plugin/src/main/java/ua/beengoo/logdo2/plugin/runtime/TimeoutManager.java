package ua.beengoo.logdo2.plugin.runtime;

import lombok.extern.slf4j.Slf4j;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ua.beengoo.logdo2.core.service.LoginService;
import ua.beengoo.logdo2.core.service.LoginStateService;
import ua.beengoo.logdo2.plugin.LogDO2;
import ua.beengoo.logdo2.plugin.actions.Action;
import ua.beengoo.logdo2.plugin.config.Config;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j(topic = "LogDO2")
public class TimeoutManager {
    private final Plugin plugin;
    private final LoginStateService state;
    private final LoginService service;
    private final Duration loginTtl;
    private final Duration ipTtl;
    private final Duration bedrockReuseWindow;

    // Scheduling state
    private final boolean folia;
    private int legacyTaskId = -1;
    private Object foliaTask = null;

    // Throttle for titles
    private final Map<UUID, Long> lastLoginTitle = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastIpTitle = new ConcurrentHashMap<>();

    public TimeoutManager(Plugin plugin, LoginStateService state, LoginService service,
                          Duration loginTtl, Duration ipTtl,
                          Duration bedrockReuseWindow) {
        this.plugin = plugin;
        this.state = state;
        this.service = service;
        this.loginTtl = loginTtl;
        this.ipTtl = ipTtl;
        this.bedrockReuseWindow = bedrockReuseWindow == null ? Duration.ofSeconds(60) : bedrockReuseWindow;
        this.folia = wasFolia();
    }

    public void start() {
        if (folia) {
            foliaTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                    plugin,
                    st -> tick(),
                    20L,
                    20L
            );
        } else {
            legacyTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 20L, 20L);
        }
    }

    public void stop() {
        if (folia) {
            if (foliaTask != null) {
                try {
                    Class<?> cls = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
                    cls.getMethod("cancel").invoke(foliaTask);
                } catch (Exception ignored) {}
                finally {
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

        final var pendingLogins = state.listPendingLogins();
        final var pendingIps = state.listPendingIpConfirms();

        for (LoginStateService.PendingLogin p : pendingLogins) {
            final UUID uuid = p.uuid();

            if (Duration.between(p.at(), now).compareTo(loginTtl) > 0) {
                runOnPlayerThread(uuid, () -> service.handleLoginTimeout(uuid));
            }

            Long last = lastLoginTitle.get(uuid);
            if (last == null || nowSec - last >= 5) {
                runOnPlayerThread(uuid, () -> {
                            if (!(Config.getFileConfiguration().getBoolean("advanced.useDialogs") && !LogDO2.getInstance().getFloodgateProvider().isBedrockPlayer(uuid))) {
                                Action.showLoginPhaseTitle(uuid);
                                if (p.bedrock()) {
                                    Action.sendBedrockHint(uuid, p.token());
                                } else {
                                    Action.sendClickableAuth(uuid, p.token());
                                }
                            }
                });
                lastLoginTitle.put(uuid, nowSec);
            }
        }

        for (LoginStateService.PendingIp p : pendingIps) {
            final UUID uuid = p.uuid();

            if (Duration.between(p.at(), now).compareTo(ipTtl) > 0) {
                runOnPlayerThread(uuid, () -> service.handleAddressConfirmTimeout(uuid));
            }

            Long last = lastIpTitle.get(uuid);
            if (last == null || nowSec - last >= 5) {
                runOnPlayerThread(uuid, () -> {
                    if (!(Config.getFileConfiguration().getBoolean("advanced.useDialogs") && !LogDO2.getInstance().getFloodgateProvider().isBedrockPlayer(uuid))){
                        Action.showIpConfirmPhaseTitle(uuid);
                    }
                });
                lastIpTitle.put(uuid, nowSec);
            }
        }

        final Set<UUID> stillPendingLogin = pendingLogins.stream().map(LoginStateService.PendingLogin::uuid).collect(java.util.stream.Collectors.toSet());
        final Set<UUID> stillPendingIp = pendingIps.stream().map(LoginStateService.PendingIp::uuid).collect(java.util.stream.Collectors.toSet());
        lastLoginTitle.keySet().removeIf(uuid -> !stillPendingLogin.contains(uuid));
        lastIpTitle.keySet().removeIf(uuid -> !stillPendingIp.contains(uuid));

        for (LoginStateService.PendingLogin p : pendingLogins) {
            if (!p.bedrock()) continue;
            UUID uuid = p.uuid();
            if (Bukkit.getPlayer(uuid) != null) continue;
            boolean withinWindow = state.recentBedrockCodeAfterLeave(uuid, bedrockReuseWindow).isPresent();
            if (!withinWindow) {
                state.clearPendingLogin(uuid);
            }
        }
    }

    private void runOnPlayerThread(UUID uuid, Runnable action) {
        if (!folia) {
            action.run();
            return;
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        player.getScheduler().run(plugin, scheduledTask -> {
            try {
                action.run();
            } catch (Throwable t) {
                log.warn("TimeoutManager action failed for {}: {}", uuid, t.getMessage());
            }
        }, null);
    }

    private boolean wasFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
