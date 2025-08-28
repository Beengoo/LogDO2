package ua.beengoo.logdo2.plugin.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;
import ua.beengoo.logdo2.api.ports.LoginStatePort;
import ua.beengoo.logdo2.core.service.LoginService;
import ua.beengoo.logdo2.plugin.integration.FloodgateHook;

import java.util.Optional;


public class PlayerListener implements Listener {
    private final LoginService loginService;
    private final FloodgateHook floodgate;
    private final LoginStatePort state;
    private final Plugin plugin;

    public PlayerListener(LoginService loginService, FloodgateHook floodgate, LoginStatePort state, Plugin plugin) {
        this.loginService = loginService;
        this.floodgate = floodgate;
        this.state = state;
        this.plugin = plugin;
    }

    // === ENTRY POINT ===
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String ip = getIp(p);
        loginService.onPlayerJoin(p.getUniqueId(), p.getName(), ip, isBedrock(p));
    }

    // === BLOCKERS ===
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent e) {
        if (!isAllowed(e.getPlayer(), Action.MOVE)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        if (!isAllowed(e.getPlayer(), Action.INTERACT)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e) {
        if (!isAllowed(e.getPlayer(), Action.CHAT)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String msg = e.getMessage();
        String root = extractRootCommand(msg);
        if (!isCommandAllowed(p, root)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (!isAllowed(e.getPlayer(), Action.DROP)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventory(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p && !isAllowed(p, Action.INVENTORY)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!isAllowed(p, Action.DAMAGE)) e.setCancelled(true);
    }

    private boolean isAllowed(Player p, Action action) {
        // If fully allowed by service (i.e., not pending states), no gating applies
        String ip = getIp(p);
        if (loginService.isActionAllowed(p.getUniqueId(), ip)) return true;

        // Determine phase
        Phase phase = getPhase(p);
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
        return plugin != null && plugin.getConfig().getBoolean(key, def);
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
        if (phase == null) return true;

        String base = (phase == Phase.LOGIN) ? "gates.login." : "gates.ipConfirm.";
        boolean commandsToggle = plugin != null && plugin.getConfig().getBoolean(base + "commands", false);
        if (commandsToggle) return true; // all commands allowed in this phase

        var list = (plugin != null) ? plugin.getConfig().getStringList(base + "commandsAllowed") : java.util.List.<String>of();
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
        try {
            return floodgate != null && floodgate.isPresent() && floodgate.isBedrock(p.getUniqueId());
        } catch (Throwable ignored) {
            return false;
        }
    }
}
