package ua.beengoo.logdo2.plugin.listeners;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import ua.beengoo.logdo2.api.ports.BanProgressRepo;
import ua.beengoo.logdo2.plugin.i18n.YamlMessages;
import ua.beengoo.logdo2.plugin.util.AuditLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class PreLoginListener implements Listener {
    private final BanProgressRepo bans;
    private final Logger log;
    private final YamlMessages msg;
    private final AuditLogger audit;

    public PreLoginListener(BanProgressRepo bans, Logger log, YamlMessages msg, AuditLogger audit) {
        this.bans = bans;
        this.log = log;
        this.msg = msg;
        this.audit = audit;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        String ip = event.getAddress().getHostAddress();
        if (ip == null || ip.isBlank()) return;

        long now = System.currentTimeMillis() / 1000;
        var rec = bans.findByIp(ip);
        if (rec.isEmpty()) return;

        long until = rec.get().lastBanUntilEpochSec();
        if (until <= now) return;

        long remain = until - now;
        Map<String, String> ph = new HashMap<>();
        ph.put("remaining", humanDuration(remain));
        String kickMsg = msg.mc("prelogin.banned", ph);

        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Component.text(kickMsg));
        log.info("[LogDO2] Blocked banned IP " + ip + " (" + event.getUniqueId() + "): " + kickMsg);
        if (audit != null) audit.log("minecraft", "prelogin_blocked_banned", java.util.Map.of(
                "ip", ip,
                "uuid", event.getUniqueId().toString()
        ));
    }

    private static String humanDuration(long seconds) {
        long s = seconds;
        long d = s / 86400; s %= 86400;
        long h = s / 3600;  s %= 3600;
        long m = s / 60;    s %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (d == 0 && h == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }
}
