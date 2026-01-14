package ua.beengoo.logdo2.plugin.listeners.game;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;
import ua.beengoo.logdo2.api.events.LoginPhase;
import ua.beengoo.logdo2.api.events.PlayerIpCheckEvent;
import ua.beengoo.logdo2.api.events.PlayerPostLoginCheckEvent;
import ua.beengoo.logdo2.core.service.LoginService;
import ua.beengoo.logdo2.core.service.LoginStateService;
import ua.beengoo.logdo2.plugin.conditions.login.LoginConditionEvaluator;
import ua.beengoo.logdo2.plugin.config.Config;
import ua.beengoo.logdo2.api.spi.providers.FloodgateProvider;
import ua.beengoo.logdo2.plugin.util.AuditLogger;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;


public class PlayerListener implements Listener {
    private final LoginService loginService;
    private final FloodgateProvider floodgateProvider;
    private final LoginStateService state;
    private final Plugin plugin;
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final AuditLogger audit;
    private final java.util.Map<java.util.UUID, Phase> lastPhase = new java.util.concurrent.ConcurrentHashMap<>();
    private final LoginConditionEvaluator conditionEvaluator;

    public PlayerListener(LoginService loginService, FloodgateProvider floodgateProvider, LoginStateService state, Plugin plugin, AuditLogger audit, LoginConditionEvaluator conditionEvaluator) {
        this.loginService = loginService;
        this.floodgateProvider = floodgateProvider;
        this.state = state;
        this.plugin = plugin;
        this.audit = audit;
        this.conditionEvaluator = conditionEvaluator;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent e) {
        Player p = e.getPlayer();
        String ip = Optional.of(e.getAddress()).map(InetAddress::getHostAddress).orElse("unknown");
        boolean bedrock = isBedrock(p);
        if (audit != null) audit.log("minecraft", "player_login_attempt", java.util.Map.of(
                "name", p.getName(),
                "uuid", p.getUniqueId().toString(),
                "ip", ip,
                "bedrock", String.valueOf(bedrock)
        ));
        var reasonOpt = conditionEvaluator.evaluateConditions(p.getUniqueId(), p.getName(), ip, bedrock);
        boolean allowed = reasonOpt.isEmpty();
        try {
            runPlayer(e.getPlayer().getUniqueId(), player -> Bukkit.getPluginManager().callEvent(
                    new PlayerPostLoginCheckEvent(p, ip, bedrock, allowed, reasonOpt.orElse(null))
            ));
        } catch (Throwable ignored) {}
        reasonOpt.ifPresent(reason -> e.disallow(PlayerLoginEvent.Result.KICK_OTHER, MINI.deserialize(reason)));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String ip = getIp(p);
        loginService.handlePlayerJoin(p.getUniqueId(), p.getName(), ip, isBedrock(p));
        // Apply visuals for current phase if any
        refreshVisuals(p);
        if (audit != null) audit.log("minecraft", "player_join", java.util.Map.of(
                "name", p.getName(),
                "uuid", p.getUniqueId().toString(),
                "ip", ip,
                "bedrock", String.valueOf(isBedrock(p))
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        try {
            if (state != null && state.isPendingLogin(p.getUniqueId())) {
                if (isBedrock(p)) {
                    state.recordBedrockLeave(p.getUniqueId());
                    if (audit != null) audit.log("minecraft", "player_quit_pending_login", java.util.Map.of(
                            "name", p.getName(),
                            "uuid", p.getUniqueId().toString(),
                            "bedrock", "true"
                    ));
                } else {
                    // For Java players, clear pending login session immediately on leave
                    state.clearPendingLogin(p.getUniqueId());
                    if (audit != null) audit.log("minecraft", "player_quit_pending_login", java.util.Map.of(
                            "name", p.getName(),
                            "uuid", p.getUniqueId().toString(),
                            "bedrock", "false"
                    ));
                }
            }
        } catch (Throwable ignored) {}
    }

    // === BLOCKERS ===
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent e) {
        if (!isAllowed(e.getPlayer(), Action.MOVE)) {
            if (Config.getFileConfiguration().getBoolean("advanced.useDialogs") && !isBedrock(e.getPlayer())) {
                if (state.getLoginPhase(e.getPlayer().getUniqueId()).equals(LoginPhase.IP_CONFIRM))
                    ua.beengoo.logdo2.plugin.actions.Action.showConfirmPhaseDialog(e.getPlayer().getUniqueId());
                else ua.beengoo.logdo2.plugin.actions.Action.showLoginPhaseDialog(e.getPlayer().getUniqueId());
            }
            e.setCancelled(true);
        }
        else refreshVisuals(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        if (!isAllowed(e.getPlayer(), Action.INTERACT)) {
            if (Config.getFileConfiguration().getBoolean("advanced.useDialogs") && !isBedrock(e.getPlayer())) {
                if (state.getLoginPhase(e.getPlayer().getUniqueId()).equals(LoginPhase.IP_CONFIRM))
                    ua.beengoo.logdo2.plugin.actions.Action.showConfirmPhaseDialog(e.getPlayer().getUniqueId());
                else ua.beengoo.logdo2.plugin.actions.Action.showLoginPhaseDialog(e.getPlayer().getUniqueId());
            }
            e.setCancelled(true);
        } else refreshVisuals(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent e) {
        if (!isAllowed(e.getPlayer(), Action.CHAT)) {
            if (Config.getFileConfiguration().getBoolean("advanced.useDialogs") && !isBedrock(e.getPlayer())) {
                if (state.getLoginPhase(e.getPlayer().getUniqueId()).equals(LoginPhase.IP_CONFIRM))
                    ua.beengoo.logdo2.plugin.actions.Action.showConfirmPhaseDialog(e.getPlayer().getUniqueId());
                else ua.beengoo.logdo2.plugin.actions.Action.showLoginPhaseDialog(e.getPlayer().getUniqueId());
            }
            e.setCancelled(true);
        }

    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String msg = e.getMessage();
        String root = extractRootCommand(msg);
        if (!isCommandAllowed(p, root)) {
            if (Config.getFileConfiguration().getBoolean("advanced.useDialogs") && !isBedrock(e.getPlayer())) {
                if (state.getLoginPhase(e.getPlayer().getUniqueId()).equals(LoginPhase.IP_CONFIRM))
                    ua.beengoo.logdo2.plugin.actions.Action.showConfirmPhaseDialog(e.getPlayer().getUniqueId());
                else ua.beengoo.logdo2.plugin.actions.Action.showLoginPhaseDialog(e.getPlayer().getUniqueId());
            }
            e.setCancelled(true);
        }
        else refreshVisuals(p);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (!isAllowed(e.getPlayer(), Action.DROP)) {
            if (Config.getFileConfiguration().getBoolean("advanced.useDialogs") && !isBedrock(e.getPlayer())) {
                if (state.getLoginPhase(e.getPlayer().getUniqueId()).equals(LoginPhase.IP_CONFIRM))
                    ua.beengoo.logdo2.plugin.actions.Action.showConfirmPhaseDialog(e.getPlayer().getUniqueId());
                else ua.beengoo.logdo2.plugin.actions.Action.showLoginPhaseDialog(e.getPlayer().getUniqueId());
            }
            e.setCancelled(true);
        }
        else refreshVisuals(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventory(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p && !isAllowed(p, Action.INVENTORY)) {
            if (Config.getFileConfiguration().getBoolean("advanced.useDialogs") && !isBedrock((Player) e.getPlayer())) {
                if (state.getLoginPhase(e.getPlayer().getUniqueId()).equals(LoginPhase.IP_CONFIRM))
                    ua.beengoo.logdo2.plugin.actions.Action.showConfirmPhaseDialog(e.getPlayer().getUniqueId());
                else ua.beengoo.logdo2.plugin.actions.Action.showLoginPhaseDialog(e.getPlayer().getUniqueId());
            }
            e.setCancelled(true);
        }
        else if (e.getPlayer() instanceof Player p) refreshVisuals(p);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!isAllowed(p, Action.DAMAGE)) {
            if (Config.getFileConfiguration().getBoolean("advanced.useDialogs") && !isBedrock((Player) e.getEntity())) {
                if (state.getLoginPhase(e.getEntity().getUniqueId()).equals(LoginPhase.IP_CONFIRM))
                    ua.beengoo.logdo2.plugin.actions.Action.showConfirmPhaseDialog(e.getEntity().getUniqueId());
                else ua.beengoo.logdo2.plugin.actions.Action.showLoginPhaseDialog(e.getEntity().getUniqueId());
            }
            e.setCancelled(true);
        }
        else refreshVisuals(p);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isAllowed(Player p, Action action) {
        // If fully allowed by service (i.e., not pending states), no gating applies
        String ip = getIp(p);
        boolean coreAllowed = loginService.isActionAllowed(p.getUniqueId(), ip);

        PlayerIpCheckEvent event = new PlayerIpCheckEvent(p, ip, coreAllowed);
        runPlayer(p.getUniqueId(), player -> Bukkit.getPluginManager().callEvent(event));
        boolean finalAllowed;
        if (event.isAllowed() != coreAllowed) {
            finalAllowed = event.isAllowed();
        } else {
            finalAllowed = coreAllowed;
        }

        if (finalAllowed) return true;

        // Determine phase
        Phase phase = getPhase(p);
        logPhaseIfChanged(p, phase);
        if (phase == null) return true;

        String base = (phase == Phase.LOGIN) ? "gates.login." : "gates.ipConfirm.";
        String key = switch (action) {
            case MOVE -> base + "move";
            case INTERACT -> base + "interact";
            case CHAT -> base + "chat";
            case COMMANDS -> base + "commands";
            case DROP -> base + "drop";
            case INVENTORY -> base + "inventory";
            case DAMAGE -> base + "damage";
        };
        boolean def = (action == Action.DAMAGE); // allow damage by default, block others
        return plugin != null && Config.getFileConfiguration().getBoolean(key, def);
    }

    private Phase getPhase(Player p) {
        if (state == null) return null;
        if (state.isPendingLogin(p.getUniqueId())) return Phase.LOGIN;
        if (state.isPendingIpConfirm(p.getUniqueId())) return Phase.IP_CONFIRM;
        return null;
    }

    private boolean isCommandAllowed(Player p, String rootCmd) {
        // If overall allowed (no gating), allow
        String ip = getIp(p);
        if (loginService.isActionAllowed(p.getUniqueId(), ip)) return true;

        Phase phase = getPhase(p);
        logPhaseIfChanged(p, phase);
        if (phase == null) return true;

        String base = (phase == Phase.LOGIN) ? "gates.login." : "gates.ipConfirm.";
        boolean commandsToggle = plugin != null && Config.getFileConfiguration().getBoolean(base + "commands", false);
        if (commandsToggle) return true; // all commands allowed in this phase

        var list = (plugin != null) ? Config.getFileConfiguration().getStringList(base + "commandsAllowed") : java.util.List.<String>of();
        String cmd = (rootCmd == null ? "" : rootCmd).toLowerCase(java.util.Locale.ROOT);
        for (String s : list) {
            if (cmd.equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    private static String extractRootCommand(String message) {
        if (message == null || message.isBlank()) return "";
        String m = message.trim();
        if (m.startsWith("/")) m = m.substring(1);
        int sp = m.indexOf(' ');
        return sp >= 0 ? m.substring(0, sp) : m;
    }

    private enum Action { MOVE, INTERACT, CHAT, COMMANDS, DROP, INVENTORY, DAMAGE }
    private enum Phase { LOGIN, IP_CONFIRM }

    private static String getIp(Player p) {
        return Optional.ofNullable(p.getAddress())
                .map(a -> a.getAddress().getHostAddress()).orElse("unknown");
    }

    private boolean isBedrock(Player p) {
        return floodgateProvider != null && floodgateProvider.isBedrockPlayer(p.getUniqueId());
    }

    // ===== visuals (blindness, hide players) =====
    private void refreshVisuals(Player p) {
        String ip = getIp(p);
        boolean fullyAllowed = loginService.isActionAllowed(p.getUniqueId(), ip);
        Phase phase = getPhase(p);
        logPhaseIfChanged(p, phase);
        if (fullyAllowed || phase == null) {
            clearVisuals(p);
            return;
        }
        String base = (phase == Phase.LOGIN) ? "gates.login." : "gates.ipConfirm.";
        boolean blind = Config.getFileConfiguration().getBoolean(base + "blindness", false);
        boolean hide = Config.getFileConfiguration().getBoolean(base + "hidePlayers", false);
        if (blind) applyBlindness(p); else clearBlindness(p);
        if (hide) hideOthers(p); else showOthers(p);
    }

    private void logPhaseIfChanged(Player p, Phase phase) {
        java.util.UUID id = p.getUniqueId();
        Phase prev;
        if (phase == null) {
            prev = lastPhase.remove(id);
        } else {
            prev = lastPhase.put(id, phase);
        }
        if (audit == null) return;
        if (prev != phase) {
            String prevStr = prev == null ? "NONE" : prev.name();
            String newStr  = phase == null ? "NONE" : phase.name();
            audit.log("minecraft", "phase_change", java.util.Map.of(
                    "uuid", p.getUniqueId().toString(),
                    "name", p.getName(),
                    "prev", prevStr,
                    "next", newStr
            ));
        }
    }

    private void applyBlindness(Player p) {
        var type = org.bukkit.potion.PotionEffectType.BLINDNESS;
        var current = p.getPotionEffect(type);
        int ticks = 20 * 3600; // 1 hour, refreshed by events
        if (current == null || current.getDuration() < ticks / 2) {
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(type, ticks, 0, true, false));
        }
    }

    private void clearBlindness(Player p) {
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
    }

    private void hideOthers(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) continue;
            p.hidePlayer(plugin, other);
        }
    }

    private void showOthers(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) continue;
            p.showPlayer(plugin, other);
        }
    }

    private void clearVisuals(Player p) {
        clearBlindness(p);
        showOthers(p);
    }
    //TODO: I know I duplicate it, I`m just lazy to make separate utility module, I promise, I`ll make it later
    private void runPlayer(UUID uuid, Consumer<Player> action) {
        if (Bukkit.isPrimaryThread()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) action.accept(p);
            return;
        }
        try {
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    try {
                        p.getScheduler().execute(plugin, () -> action.accept(p), null, 0L);
                    } catch (Throwable ignored) {
                        action.accept(p);
                    }
                }
            });
        } catch (Throwable ignored) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) action.accept(p);
            });
        }
    }
}
