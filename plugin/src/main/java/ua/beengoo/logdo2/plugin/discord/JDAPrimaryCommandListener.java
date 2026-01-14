package ua.beengoo.logdo2.plugin.discord;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ua.beengoo.logdo2.api.spi.providers.MessagesProvider;
import ua.beengoo.logdo2.api.spi.repo.AccountsRepo;
import ua.beengoo.logdo2.api.spi.repo.ProfileRepo;

import java.util.Map;
import java.util.UUID;

@Slf4j(topic = "LogDO2")
public class JDAPrimaryCommandListener extends ListenerAdapter {
    private final AccountsRepo accountsRepo;
    private final ProfileRepo profileRepo;
    private final MessagesProvider msg;
    private final ua.beengoo.logdo2.plugin.util.AuditLogger audit;

    public JDAPrimaryCommandListener(AccountsRepo accountsRepo, ProfileRepo profileRepo,
                                    MessagesProvider msg, ua.beengoo.logdo2.plugin.util.AuditLogger audit) {
        this.accountsRepo = accountsRepo;
        this.profileRepo = profileRepo;
        this.msg = msg;
        this.audit = audit;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("primary")) return;

        long discordId = event.getUser().getIdLong();

        try {
            // Get all linked accounts with primary flag
            Map<UUID, Boolean> links = accountsRepo.findLinksWithPrimaryFlag(discordId);

            if (links.isEmpty()) {
                event.reply(msg.raw("discord.slash_primary_no_accounts"))
                    .setEphemeral(true)
                    .queue();
                return;
            }

            if (links.size() == 1) {
                event.reply(msg.raw("discord.slash_primary_only_one"))
                    .setEphemeral(true)
                    .queue();
                return;
            }

            // Build dropdown menu
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("primary-select")
                    .setPlaceholder(msg.raw("discord.slash_primary_placeholder"));

            for (Map.Entry<UUID, Boolean> entry : links.entrySet()) {
                UUID uuid = entry.getKey();
                boolean isPrimary = entry.getValue();

                String name = profileRepo.findNameByUuid(uuid).orElse(uuid.toString());
                String label = name + (isPrimary ? " ⭐" : "");
                String description = isPrimary ? msg.raw("discord.slash_primary_current") : msg.raw("discord.slash_primary_make_primary");

                menuBuilder.addOption(label, uuid.toString(), description);
            }

            event.reply(msg.raw("discord.slash_primary_choose"))
                    .addComponents(ActionRow.of(menuBuilder.build()))
                    .setEphemeral(true)
                    .queue();

            if (audit != null) audit.log("discord", "slash_primary_opened", Map.of(
                    "discord", String.valueOf(discordId),
                    "accounts", String.valueOf(links.size())
            ));

        } catch (Exception e) {
            log.warn("Error while executing primary command: {}", e.getMessage());
            event.reply(msg.raw("discord.slash_primary_error"))
                    .setEphemeral(true)
                    .queue();
        }
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals("primary-select")) return;

        long discordId = event.getUser().getIdLong();
        String selectedValue = event.getValues().get(0);

        try {
            UUID selectedUuid = UUID.fromString(selectedValue);

            // Check if this account belongs to the user
            Map<UUID, Boolean> links = accountsRepo.findLinksWithPrimaryFlag(discordId);
            if (!links.containsKey(selectedUuid)) {
                event.reply(msg.raw("discord.slash_primary_invalid"))
                        .setEphemeral(true)
                        .queue();
                return;
            }

            // Set as primary
            accountsRepo.setPrimaryLink(discordId, selectedUuid);

            String name = profileRepo.findNameByUuid(selectedUuid).orElse(selectedUuid.toString());
            event.reply(msg.raw("discord.slash_primary_success")
                            .replace("{name}", name))
                    .setEphemeral(true)
                    .queue();

            if (audit != null) audit.log("discord", "slash_primary_changed", Map.of(
                    "discord", String.valueOf(discordId),
                    "new_primary", selectedUuid.toString(),
                    "name", name
            ));

        } catch (Exception e) {
            log.warn("Error while changing primary account: {}", e.getMessage());
            event.reply(msg.raw("discord.slash_primary_error"))
                    .setEphemeral(true)
                    .queue();
        }
    }
}