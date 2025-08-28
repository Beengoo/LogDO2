package ua.beengoo.logdo2.plugin.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ua.beengoo.logdo2.api.ports.MessagesPort;
import ua.beengoo.logdo2.api.ports.ProfileRepo;
import ua.beengoo.logdo2.core.service.LoginService;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class JdaDiscordButtonListener extends ListenerAdapter {
    private final LoginService loginService;
    private final ProfileRepo profileRepo;
    private final MessagesPort msg;
    private final Logger log;
    private final ua.beengoo.logdo2.plugin.util.AuditLogger audit;

    public JdaDiscordButtonListener(LoginService loginService, ProfileRepo profileRepo, MessagesPort msg, Logger log, ua.beengoo.logdo2.plugin.util.AuditLogger audit) {
        this.loginService = loginService;
        this.profileRepo = profileRepo;
        this.msg = msg;
        this.log = log;
        this.audit = audit;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String cid = event.getComponentId(); // "ip:accept:<uuid>" | "ip:reject:<uuid>"
        if (cid == null || !cid.startsWith("ip:")) return;

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
                    loginService.onDiscordIpConfirm(uuid, discordId);
                    if (audit != null) audit.log("discord", "ip_confirm_accept", java.util.Map.of(
                            "discord", String.valueOf(discordId),
                            "player", uuid.toString()
                    ));
                    updateEmbed(event, playerName, uuid, true);
                }
                case "reject" -> {
                    loginService.onDiscordIpReject(uuid, discordId);
                    if (audit != null) audit.log("discord", "ip_confirm_reject", java.util.Map.of(
                            "discord", String.valueOf(discordId),
                            "player", uuid.toString()
                    ));
                    updateEmbed(event, playerName, uuid, false);
                }
                default -> {}
            }
        } catch (Exception ex) {
            log.warning("[LogDO2] Button handler error: " + ex.getMessage());
            // Все одно прибираємо кнопки, щоб не спамили
            safeDisableButtons(event);
        }
    }

    private void updateEmbed(ButtonInteractionEvent event, String name, UUID uuid, boolean confirmed) {
        var embeds = event.getMessage().getEmbeds();
        EmbedBuilder eb = embeds.isEmpty() ? new EmbedBuilder() : new EmbedBuilder(embeds.get(0));

        Map<String, String> ph = new HashMap<>();
        ph.put("name", name);
        ph.put("uuid", uuid.toString());

        if (confirmed) {
            eb.setTitle(msg.raw("discord.ip_confirm_title_confirmed")
                    .replace("{name}", name).replace("{uuid}", uuid.toString()));
            eb.setColor(Color.GREEN);
        } else {
            eb.setTitle(msg.raw("discord.ip_confirm_title_rejected")
                    .replace("{name}", name).replace("{uuid}", uuid.toString()));
            eb.setColor(Color.RED);
        }

        // Редагуємо існуюче повідомлення: новий заголовок, прибираємо кнопки
        event.editMessageEmbeds(eb.build())
                .setComponents() // порожньо = без кнопок
                .queue();
    }

    private void safeDisableButtons(ButtonInteractionEvent event) {
        var embeds = event.getMessage().getEmbeds();
        EmbedBuilder eb = embeds.isEmpty() ? new EmbedBuilder() : new EmbedBuilder(embeds.get(0));
        event.editMessageEmbeds(eb.build()).setComponents().queue();
    }
}
