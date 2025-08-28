package ua.beengoo.logdo2.plugin.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ua.beengoo.logdo2.api.ports.MessagesPort;
import ua.beengoo.logdo2.core.service.LoginService;

import java.util.logging.Logger;

public class JdaSlashLoginListener extends ListenerAdapter {
    private final LoginService loginService;
    private final Logger log;
    private final MessagesPort msg;
    private final ua.beengoo.logdo2.plugin.util.AuditLogger audit;

    public JdaSlashLoginListener(LoginService loginService, Logger log, MessagesPort msg, ua.beengoo.logdo2.plugin.util.AuditLogger audit) {
        this.loginService = loginService;
        this.log = log;
        this.msg = msg;
        this.audit = audit;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("login")) return;
        var opt = event.getOption("code");
        if (opt == null) {
            event.reply(msg.raw("discord.slash_login_missing_code")).setEphemeral(true).queue();
            return;
        }
        String code = opt.getAsString();
        long did = event.getUser().getIdLong();

        boolean ok = false;
        try {
            ok = loginService.onDiscordSlashLogin(code, did);
        } catch (Exception e) {
            log.warning("Slash /login error: " + e.getMessage());
        }
        if (audit != null) audit.log("discord", "slash_login", java.util.Map.of(
                "discord", String.valueOf(did),
                "ok", String.valueOf(ok)
        ));
        if (ok) event.reply(msg.raw("discord.slash_login_ok")).setEphemeral(true).queue();
        else    event.reply(msg.raw("discord.slash_login_invalid")).setEphemeral(true).queue();
    }
}
