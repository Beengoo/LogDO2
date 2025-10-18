package ua.beengoo.logdo2.plugin.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import ua.beengoo.logdo2.api.events.LogDO2ReloadEvent;
import ua.beengoo.logdo2.plugin.LogDO2;
import ua.beengoo.logdo2.plugin.config.Config;

public class ReloadListener implements Listener {

    private final LogDO2 logDO2;

    public ReloadListener(LogDO2 logDO2) {
        this.logDO2 = logDO2;
    }

    @EventHandler
    public void onPluginReload(LogDO2ReloadEvent event) {
        logDO2.getLoginEndpoint().restart(Config.getFileConfiguration().getInt("web.port"));
    }

}
