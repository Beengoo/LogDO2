package ua.beengoo.logdo2.plugin.adapters.discord;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.components.buttons.Button;
import ua.beengoo.logdo2.api.spi.providers.DiscordMessagesProvider;
import ua.beengoo.logdo2.api.spi.providers.MessagesProvider;

import java.awt.*;
import java.time.Instant;
import java.util.UUID;

@Slf4j(topic = "LogDO2")
public class JdaDiscordDmAdapter implements DiscordMessagesProvider {
    private final JDA jda;
    private final MessagesProvider msg;

    public JdaDiscordDmAdapter(JDA jda, MessagesProvider msg) {
        this.jda = jda;
        this.msg = msg;
    }

    @Override
    public void sendGreetingsMessage(long discordId, UUID mcUuid, String playerName) {
        jda.retrieveUserById(discordId).queue(user -> {
            String title = msg.raw("discord.ip_confirm_title");
            String body  = msg.raw("discord.first_login_dm")
                    .replace("{name}", playerName)
                    .replace("{uuid}", mcUuid.toString());

            EmbedBuilder eb = new EmbedBuilder()
                    .setTitle(title)
                    .setDescription(body)
                    .setColor(Color.GREEN)
                    .setTimestamp(Instant.now());

            openDmAndSendEmbed(user, eb);
        }, ex -> log.warn("Unable to retrieve user", ex));
    }

    @Override
    public void sendLocationConfirmMessage(long discordId, UUID mcUuid, String playerName, String newIp) {
        jda.retrieveUserById(discordId).queue(user -> {
            String title = msg.raw("discord.ip_confirm_title");
            String body  = msg.raw("discord.ip_confirm_body")
                    .replace("{name}", playerName)
                    .replace("{uuid}", mcUuid.toString())
                    .replace("{ip}", newIp);

            EmbedBuilder eb = new EmbedBuilder()
                    .setTitle(title)
                    .setDescription(body)
                    .setColor(Color.YELLOW)
                    .setTimestamp(Instant.now());

            String cidAccept = "ip:accept:" + mcUuid;
            String cidReject = "ip:reject:" + mcUuid;

            user.openPrivateChannel().queue(
                    c -> c.sendMessageEmbeds(eb.build())
                            .setComponents(
                                    ActionRow.of(
                                            Button.success(cidAccept, msg.raw("discord.ip_confirm_button_accept")),
                                            Button.danger(cidReject, msg.raw("discord.ip_confirm_button_reject"))
                                    )
                            ).queue(
                                    s -> {},
                                    f -> log.warn("Unable to send user direct message", f)
                            )
            );
        }, ex -> log.warn("Unable to retrieve user", ex));
    }

    @Override
    public void sendOAuth2URLMessage(long discordId, String url) {
        jda.retrieveUserById(discordId).queue(user -> {
            String text = msg.raw("discord.finalize_oauth_link").replace("{url}", url);
            user.openPrivateChannel().queue(
                    ch -> ch.sendMessage(text).queue(
                            s  -> {},
                            ex -> log.warn("Unable to send direct message to user", ex)
                    ),
                    ex -> log.warn("Unable to send direct message to user", ex)
            );
        }, ex -> log.warn("Unable to send direct message to user", ex));
    }

    private void openDmAndSendEmbed(User user, EmbedBuilder eb) {
        user.openPrivateChannel().queue(
                ch -> ch.sendMessageEmbeds(eb.build()).queue(
                        s  -> {},
                        ex -> log.warn("Unable to send direct message to user", ex)
                ),
                ex -> log.warn("Unable to open direct message with user", ex)
        );
    }
}
