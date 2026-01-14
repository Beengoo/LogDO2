package ua.beengoo.logdo2.plugin.discord;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ua.beengoo.logdo2.api.spi.providers.MessagesProvider;
import ua.beengoo.logdo2.api.spi.repo.ProfileRepo;
import ua.beengoo.logdo2.core.service.LoginService;
import ua.beengoo.logdo2.plugin.util.AuditLogger;

import java.awt.*;
import java.util.UUID;

@Slf4j(topic = "LogDO2")
public class JDAButtonInteractionListener extends ListenerAdapter {
    private final LoginService loginService;
    private final ProfileRepo profileRepo;
    private final MessagesProvider msg;
    private final AuditLogger audit;

    public JDAButtonInteractionListener(LoginService loginService, ProfileRepo profileRepo, MessagesProvider msg, AuditLogger audit) {
        this.loginService = loginService;
        this.profileRepo = profileRepo;
        this.msg = msg;
        this.audit = audit;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String cid = event.getComponentId(); // "ip:accept:<uuid>" | "ip:reject:<uuid>"
        if (!cid.startsWith("ip:")) return;

        String[] parts = cid.split(":");
        if (parts.length != 3) return;

        String action = parts[1];
        UUID uuid;
        try { uuid = UUID.fromString(parts[2]); }
        catch (IllegalArgumentException e) { return; }

        long discordId = event.getUser().getIdLong();
        String playerName = profileRepo.findNameByUuid(uuid).orElse("player");

        try {
            switch (action) {
                case "accept" -> {
                    loginService.acceptNewAddress(uuid, discordId);
                    if (audit != null) audit.log("discord", "ip_confirm_accept", java.util.Map.of(
                            "discord", String.valueOf(discordId),
                            "player", uuid.toString()
                    ));
                    updateEmbed(event, playerName, uuid, true);
                }
                case "reject" -> {
                    loginService.rejectNewAddress(uuid, discordId);
                    if (audit != null) audit.log("discord", "ip_confirm_reject", java.util.Map.of(
                            "discord", String.valueOf(discordId),
                            "player", uuid.toString()
                    ));
                    updateEmbed(event, playerName, uuid, false);
                }
                default -> {}
            }
        } catch (Exception ex) {
            log.warn("Button handler error: {}", ex.getMessage());
            safeDisableButtons(event);
        }
    }

    private void updateEmbed(ButtonInteractionEvent event, String name, UUID uuid, boolean confirmed) {
        var embeds = event.getMessage().getEmbeds();
        EmbedBuilder eb = embeds.isEmpty() ? new EmbedBuilder() : new EmbedBuilder(embeds.getFirst());

        if (confirmed) {
            eb.setTitle(msg.raw("discord.ip_confirm_title_confirmed")
                    .replace("{name}", name).replace("{uuid}", uuid.toString()));
            eb.setColor(Color.GREEN);
        } else {
            eb.setTitle(msg.raw("discord.ip_confirm_title_rejected")
                    .replace("{name}", name).replace("{uuid}", uuid.toString()));
            eb.setColor(Color.RED);
        }

        event.editMessageEmbeds(eb.build())
                .setComponents()
                .queue();
    }

    private void safeDisableButtons(ButtonInteractionEvent event) {
        var embeds = event.getMessage().getEmbeds();
        EmbedBuilder eb = embeds.isEmpty() ? new EmbedBuilder() : new EmbedBuilder(embeds.getFirst());
        event.editMessageEmbeds(eb.build()).setComponents().queue();
    }
}
