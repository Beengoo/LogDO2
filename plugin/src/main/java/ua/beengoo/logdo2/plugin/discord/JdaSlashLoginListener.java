package ua.beengoo.logdo2.plugin.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ua.beengoo.logdo2.core.service.LoginService;

import java.util.logging.Logger;

public class JdaSlashLoginListener extends ListenerAdapter {
    private final LoginService loginService;
    private final Logger log;

    public JdaSlashLoginListener(LoginService loginService, Logger log) {
        this.loginService = loginService;
        this.log = log;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("login")) return;
        var opt = event.getOption("code");
        if (opt == null) {
            event.reply("Missing required option: code").setEphemeral(true).queue();
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
        if (ok) event.reply("✅ Linked. Check your in-game status and finish OAuth if prompted.").setEphemeral(true).queue();
        else    event.reply("❌ Invalid or expired code. Join the server again to get a fresh one.").setEphemeral(true).queue();
    }
}
