package ua.beengoo.logdo2.plugin.adapters.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ua.beengoo.logdo2.api.ports.DiscordDmPort;
import ua.beengoo.logdo2.api.ports.MessagesPort;

import java.awt.*;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;

public class JdaDiscordDmAdapter implements DiscordDmPort {
    private final JDA jda;
    private final Logger log;
    private final MessagesPort msg;

    public JdaDiscordDmAdapter(JDA jda, Logger log, MessagesPort msg) {
        this.jda = jda;
        this.log = log;
        this.msg = msg;
    }

    @Override
    public void sendFirstLoginDm(long discordId, UUID mcUuid, String playerName, String publicUrl) {
        jda.retrieveUserById(discordId).queue(user -> {
            String title = msg.raw("discord.ip_confirm_title"); // або додай окремий discord.first_login_title
            String body  = msg.raw("discord.first_login_dm")
                    .replace("{name}", playerName)
                    .replace("{uuid}", mcUuid.toString());

            EmbedBuilder eb = new EmbedBuilder()
                    .setTitle(title)
                    .setDescription(body)
                    .setColor(Color.GREEN)
                    .setTimestamp(Instant.now());

            openDmAndSendEmbed(user, eb);
        }, ex -> log.warning("[LogDO2] retrieveUserById failed (firstLogin): " + ex.getMessage()));
    }

    @Override
    public void sendIpConfirmDm(long discordId, UUID mcUuid, String playerName, String newIp) {
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
                    ch -> ch.sendMessageEmbeds(eb.build())
                            .setActionRow(
                                    Button.success(cidAccept, msg.raw("discord.ip_confirm_button_accept")),
                                    Button.danger (cidReject, msg.raw("discord.ip_confirm_button_reject"))
                            ).queue(
                                    s  -> {},
                                    ex -> log.warning("[LogDO2] DM send failed (ipConfirm): " + ex.getMessage())
                            ),
                    ex -> log.warning("[LogDO2] Open DM failed (ipConfirm): " + ex.getMessage())
            );
        }, ex -> log.warning("[LogDO2] retrieveUserById failed (ipConfirm): " + ex.getMessage()));
    }

    @Override
    public void sendFinalizeOAuthLink(long discordId, String url) {
        jda.retrieveUserById(discordId).queue(user -> {
            String text = msg.raw("discord.finalize_oauth_link").replace("{url}", url);
            user.openPrivateChannel().queue(
                    ch -> ch.sendMessage(text).queue(
                            s  -> {},
                            ex -> log.warning("[LogDO2] DM send failed (finalizeOAuth): " + ex.getMessage())
                    ),
                    ex -> log.warning("[LogDO2] Open DM failed (finalizeOAuth): " + ex.getMessage())
            );
        }, ex -> log.warning("[LogDO2] retrieveUserById failed (finalizeOAuth): " + ex.getMessage()));
    }

    private void openDmAndSendEmbed(User user, EmbedBuilder eb) {
        user.openPrivateChannel().queue(
                ch -> ch.sendMessageEmbeds(eb.build()).queue(
                        s  -> {},
                        ex -> log.warning("[LogDO2] DM send failed (" + "firstLogin" + "): " + ex.getMessage())
                ),
                ex -> log.warning("[LogDO2] Open DM failed (" + "firstLogin" + "): " + ex.getMessage())
        );
    }
}
