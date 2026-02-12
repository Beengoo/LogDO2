package ua.beengoo.logdo2.plugin.actions;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ua.beengoo.logdo2.api.spi.providers.MessagesProvider;
import ua.beengoo.logdo2.api.spi.providers.Properties;
import ua.beengoo.logdo2.core.service.LoginStateService;
import ua.beengoo.logdo2.plugin.LogDO2;
import ua.beengoo.logdo2.plugin.config.Config;
import ua.beengoo.logdo2.plugin.props.LogDO2PropertiesManager;
import ua.beengoo.logdo2.plugin.util.StringUtil;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@SuppressWarnings("UnstableApiUsage")
public class Action {
    public static final MiniMessage MINI = MiniMessage.miniMessage();

    public static void sendClickableAuth(UUID uuid, String token) {
        runPlayer(uuid, p -> {
            Component comp = MINI.deserialize(LogDO2.getInstance().getMessages().mc("chat.auth_link_text"))
                    .hoverEvent(
                            HoverEvent.showText(
                                    MINI.deserialize(LogDO2.getInstance().getMessages().mc("chat.auth_link_hover"))
                            )
                    )
                    .clickEvent(ClickEvent.openUrl(LogDO2.getInstance().getWebServerInfo().getPublicLoginURL() + "?state=" + token));
            p.sendMessage(comp);
        });
    }

    public static void sendBedrockHint(UUID uuid, String code) {
        runPlayer(uuid, p -> p.sendMessage(
                MINI.deserialize(LogDO2.getInstance().getMessages().mc("login.bedrock.code_hint", Map.of("code", code)))
        ));
    }

    public static void sendTitle(UUID uuid, String title, String subtitle) {
        runPlayer(uuid, p -> p.showTitle(
                Title.title(
                        MINI.deserialize(title),
                        MINI.deserialize(subtitle),
                        Title.Times.times(Duration.ZERO, Duration.of(24, ChronoUnit.HOURS), Duration.ZERO)
                )
        ));
    }

    public static void showLoginPhaseTitle(UUID uuid) {
        sendTitle(uuid, LogDO2.getInstance().getMessages().mc("login.first_join.title"),
                LogDO2.getInstance().getMessages().mc("login.first_join.subtitle")
        );
    }

    public static void showIpConfirmPhaseTitle(UUID uuid) {
        sendTitle(uuid, LogDO2.getInstance().getMessages().mc("ip.unconfirmed.title"),
                LogDO2.getInstance().getMessages().mc("ip.unconfirmed.subtitle"));
    }

    public static void clearPhaseTitle(UUID uuid) {
        runPlayer(uuid, p -> {
            try { p.clearTitle(); } catch (Throwable ignored) { /* older API fallback */ }
            try { p.resetTitle(); } catch (Throwable ignored) { /* older API fallback */ }
            try { p.closeDialog(); } catch (Throwable ignored) { /* older API fallback */ }
        });
    }

    public static void sendActionBar(UUID uuid, String msgLine) {
        runPlayer(uuid, p -> p.sendActionBar(MINI.deserialize(msgLine)));
    }

    public static void runPlayer(UUID uuid, Consumer<Player> action) {
        if (Bukkit.isPrimaryThread()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) action.accept(p);
            return;
        }
        try {
            Bukkit.getGlobalRegionScheduler().execute(LogDO2.getInstance(), () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    try {
                        p.getScheduler().execute(LogDO2.getInstance(), () -> action.accept(p), null, 0L);
                    } catch (Throwable ignored) {
                        action.accept(p);
                    }
                }
            });
        } catch (Throwable ignored) {
            Bukkit.getScheduler().runTask(LogDO2.getInstance(), () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) action.accept(p);
            });
        }
    }

    public static void kick(UUID uuid, String reason) {
        try {
            Bukkit.getGlobalRegionScheduler().execute(LogDO2.getInstance(), () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    try {
                        p.getScheduler().execute(LogDO2.getInstance(), () -> p.kick(MINI.deserialize(reason)), null, 1L);
                    } catch (Throwable ignored) {
                        p.kick(MINI.deserialize(reason));
                    }
                }
            });
        } catch (Throwable ignored) {
            Bukkit.getScheduler().runTaskLater(LogDO2.getInstance(), () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.kick(MINI.deserialize(reason));
            }, 1L);
        }
    }

    public static long applyProgressiveBan(String ip) {
        Properties props = LogDO2PropertiesManager.getINSTANCE().getSnapshot();
        if (!props.bansEnabled || ip == null || ip.isBlank()) return 0L;

        long now = System.currentTimeMillis() / 1000;
        var recOpt = LogDO2.getInstance().getBanProgressRepo().findByIp(ip);
        int attempts = 0;
        long lastAttempt;

        if (recOpt.isPresent()) {
            var rec = recOpt.get();
            attempts = rec.attempts();
            lastAttempt = rec.lastAttemptEpochSec();
            if (props.banTrackWindowSec > 0 && now - lastAttempt > props.banTrackWindowSec) {
                attempts = 0;
            }
        }

        attempts += 1;
        double pow = Math.pow(props.banMultiplier, Math.max(0, attempts - 1));
        long dur = (long) Math.floor(props.banBaseSec * pow);
        if (dur > props.banMaxSec) dur = props.banMaxSec;

        long untilSec = now + dur;
        LogDO2.getInstance().getBanProgressRepo().upsert(ip, attempts, now, untilSec);
        return dur;
    }

    public static void showLoginPhaseDialog(@NotNull UUID uniqueId) {
        showLoginPhaseDialog(uniqueId, null);
    }

    public static void showLoginPhaseDialog(@NotNull UUID uniqueId, String _token) {
        runPlayer(uniqueId, player -> {
            String token = null;
            if (_token == null) {
                for (LoginStateService.PendingLogin pl: LogDO2.getInstance().getLoginStatePort().listPendingLogins()) {
                    if (pl.uuid().equals(uniqueId)) {
                        token = pl.token();
                    }
                }
            } else token = _token;
            String loginUrl = LogDO2.getInstance().getWebServerInfo().getPublicLoginURL() + "?state=" + token;
            boolean closeable = Config.getFileConfiguration().getBoolean("gates.login.move");
            MessagesProvider m = LogDO2.getInstance().getMessages();
            player.showDialog(Dialog.create(builder -> builder.empty()
                    .base(DialogBase.builder(MINI.deserialize(m.mc("login.first_join.dialog.title")))
                            .body(List.of(DialogBody.plainMessage(MINI.deserialize(m.mc("login.first_join.dialog.body")), 1024)))
                            .canCloseWithEscape(closeable)
                            .pause(false)
                            .build())
                    .type(DialogType.multiAction(
                            List.of(ActionButton.builder(MINI.deserialize(m.mc("login.first_join.dialog.actions.authorize")))
                                    .action(DialogAction.staticAction(ClickEvent.openUrl(loginUrl)))
                                    .build())
                    ).exitAction(closeable? ActionButton.builder(MINI.deserialize(m.mc("login.first_join.dialog.actions.close")))
                            .build() : null).build())));
        });
    }

    public static void showConfirmPhaseDialog(@NotNull UUID uniqueId) {
        runPlayer(uniqueId, player -> {
            boolean closeable = Config.getFileConfiguration().getBoolean("gates.ipConfirm.move");
            MessagesProvider m = LogDO2.getInstance().getMessages();
            player.showDialog(Dialog.create(builder -> builder.empty()
                    .base(DialogBase.builder(MINI.deserialize(m.mc("ip.unconfirmed.dialog.title")))
                            .body(List.of(DialogBody.plainMessage(MINI.deserialize(m.mc("ip.unconfirmed.dialog.body")), 1024)))
                            .canCloseWithEscape(closeable)
                            .pause(false)
                            .build())
                    .type(DialogType.multiAction(
                            List.of(ActionButton.builder(MINI.deserialize(m.mc("ip.unconfirmed.dialog.actions.bot_profile")))
                                            .action(DialogAction.staticAction(ClickEvent.openUrl("https://discord.com/users/"+LogDO2.getInstance().getJda().getSelfUser().getId())))
                                    .build())
                    ).exitAction(closeable? ActionButton.builder(MINI.deserialize(m.mc("ip.unconfirmed.dialog.actions.close"))).build() : null).build())));
        });
    }
}
