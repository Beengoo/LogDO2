package ua.beengoo.logdo2.plugin.discord;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ua.beengoo.logdo2.api.spi.providers.MessagesProvider;
import ua.beengoo.logdo2.core.service.LoginService;

@Slf4j(topic = "LogDO2")
public class JDALoginCommandListener extends ListenerAdapter {
    private final LoginService loginService;
    private final MessagesProvider msg;
    private final ua.beengoo.logdo2.plugin.util.AuditLogger audit;

    public JDALoginCommandListener(LoginService loginService, MessagesProvider msg, ua.beengoo.logdo2.plugin.util.AuditLogger audit) {
        this.loginService = loginService;
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
            ok = loginService.handleBedrockLoginCommand(code, did);
        } catch (Exception e) {
            log.warn("Error while executing login command: {}", e.getMessage());
        }
        if (audit != null) audit.log("discord", "slash_login", java.util.Map.of(
                "discord", String.valueOf(did),
                "ok", String.valueOf(ok)
        ));
        if (ok) event.reply(msg.raw("discord.slash_login_ok")).setEphemeral(true).queue();
        else    event.reply(msg.raw("discord.slash_login_invalid")).setEphemeral(true).queue();
    }
}
