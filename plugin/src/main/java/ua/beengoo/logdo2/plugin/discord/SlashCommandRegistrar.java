package ua.beengoo.logdo2.plugin.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.OptionType;

public class SlashCommandRegistrar {
    public static void register(JDA jda) {
        jda.updateCommands().addCommands(
                Commands.slash("login", "Finish linking your Bedrock account with one-time code")
                        .addOption(OptionType.STRING, "code", "One-time code from in-game", true)
        ).queue();
    }
}
