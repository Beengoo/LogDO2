package ua.beengoo.logdo2.plugin.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import ua.beengoo.logdo2.api.events.PlayerLoginPhaseEnterEvent;
import ua.beengoo.logdo2.api.events.PlayerLoginPhaseExitEvent;
import ua.beengoo.logdo2.plugin.LogDO2;
import ua.beengoo.logdo2.plugin.actions.Action;
import ua.beengoo.logdo2.plugin.config.Config;
import ua.beengoo.logdo2.plugin.util.StringUtil;

import java.util.Map;

public class LogDO2Listener implements Listener {

    @EventHandler
    public void onPlayerLoginPhaseEnter(PlayerLoginPhaseEnterEvent event) {
        switch (event.getPhase()) {
            case LOGIN -> {
                if (event.getData().bedrock()) {
                    Action.showLoginPhaseTitle(event.getPlayer().getUniqueId());
                    Action.sendBedrockHint(event.getPlayer().getUniqueId(), event.getData().token());
                } else {
                    if (Config.getFileConfiguration().getBoolean("advanced.useDialogs")) {
                        Action.showLoginPhaseDialog(event.getPlayer().getUniqueId(), event.getData().token());
                    } else {
                        Action.showLoginPhaseTitle(event.getPlayer().getUniqueId());
                        Action.sendClickableAuth(event.getPlayer().getUniqueId(), event.getData().token());
                    }
                }
            }
            case IP_CONFIRM -> {
                if (Config.getFileConfiguration().getBoolean("advanced.useDialogs")) {
                    Action.showConfirmPhaseDialog(event.getPlayer().getUniqueId());
                } else {
                    Action.showIpConfirmPhaseTitle(event.getPlayer().getUniqueId());
                }

            }
        }
    }

    @EventHandler
    public void onPlayerLoginPhaseExit(PlayerLoginPhaseExitEvent event) {
        switch (event.getLoginExitReason()) {
            case LOGIN_SUCCESS -> {
                Action.clearPhaseTitle(event.getPlayer().getUniqueId());
                Action.sendActionBar(
                        event.getPlayer().getUniqueId(),
                        LogDO2.getInstance().getMessages().mc("login.linked_actionbar")
                );
            }
            case LOGIN_TIMEOUT -> {
                Action.kick(
                        event.getPlayer().getUniqueId(),
                        LogDO2.getInstance().getMessages().mc("timeouts.login_kick")
                );
            }
            case IP_CONFIRM_TIMEOUT -> {
                Action.kick(
                        event.getPlayer().getUniqueId(),
                        LogDO2.getInstance().getMessages().mc("timeouts.ip_kick")
                );
            }
            case IP_CONFIRM_REJECT -> {
                var pending = LogDO2.getInstance().getLoginStatePort()
                        .consumePendingIpConfirm(event.getPlayer().getUniqueId());
                if (pending == null) return;

                long durSec = Action.applyProgressiveBan(pending.newIp());
                Action.kick(
                        event.getPlayer().getUniqueId(),
                        LogDO2.getInstance().getMessages().mc("ip.reject_kick",
                                Map.of("duration", StringUtil.formattedDuration(durSec)))
                );
            }
            case IP_CONFIRM_CONFIRMED -> {
                Action.clearPhaseTitle(event.getPlayer().getUniqueId());
                Action.sendActionBar(
                        event.getPlayer().getUniqueId(),
                        LogDO2.getInstance().getMessages().mc("ip.confirm_actionbar")
                );
            }
        }
    }



}
