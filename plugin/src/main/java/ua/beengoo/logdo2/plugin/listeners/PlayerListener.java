package ua.beengoo.logdo2.plugin.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import ua.beengoo.logdo2.core.service.LoginService;
import ua.beengoo.logdo2.plugin.integration.FloodgateHook;

import java.util.Optional;


public class PlayerListener implements Listener {
    private final LoginService loginService;
    private final FloodgateHook floodgate;

    public PlayerListener(LoginService loginService, FloodgateHook floodgate) {
        this.loginService = loginService;
        this.floodgate = floodgate;
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
        if (shouldBlock(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        if (shouldBlock(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e) {
        if (shouldBlock(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (shouldBlock(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (shouldBlock(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventory(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p && shouldBlock(p)) e.setCancelled(true);
    }

    private boolean shouldBlock(Player p) {
        String ip = getIp(p);
        return !loginService.isActionAllowed(p.getUniqueId(), ip);
    }

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
