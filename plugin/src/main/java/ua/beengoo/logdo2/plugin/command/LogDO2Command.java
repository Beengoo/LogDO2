package ua.beengoo.logdo2.plugin.command;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ua.beengoo.logdo2.api.LogDO2Api;
import ua.beengoo.logdo2.api.entity.LinkInfo;
import ua.beengoo.logdo2.api.entity.LogDO2Profile;
import ua.beengoo.logdo2.api.entity.LogDO2ProfileStatus;
import ua.beengoo.logdo2.api.events.LogDO2ReloadEvent;
import ua.beengoo.logdo2.api.spi.repo.*;
import ua.beengoo.logdo2.api.spi.providers.MessagesProvider;
import ua.beengoo.logdo2.core.service.LoginStateService;
import ua.beengoo.logdo2.plugin.config.Config;
import ua.beengoo.logdo2.plugin.i18n.YamlMessages;
import ua.beengoo.logdo2.plugin.util.AuditLogger;

import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j(topic = "LogDO2")
public class LogDO2Command implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter LOOKUP_DT = DateTimeFormatter.ofPattern("dd-MM-yy hh:mm a", Locale.ENGLISH);
    private static final List<String> SUBS = List.of("help", "lookup", "link", "logout", "forgive", "bypass", "reload", "force-relogin");
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final long CONFIRMATION_TIMEOUT_MS = 30_000; // 30 seconds

    // Track pending force-relogin confirmations (sender name -> timestamp)
    private static final Map<String, Long> pendingForceReloginConfirmations = new java.util.concurrent.ConcurrentHashMap<>();

    private final LogDO2Api api;
    private final AccountsRepo accountsRepo;
    private final ProfileRepo profileRepo;
    private final BanProgressRepo banProgressRepo;
    private final DiscordUserRepo discordUserRepo;
    private final MessagesProvider msg;
    private final AuditLogger audit;
    private final JDA jda;

    public LogDO2Command(LogDO2Api api,
                         AccountsRepo accountsRepo,
                         ProfileRepo profileRepo,
                         BanProgressRepo banProgressRepo,
                         DiscordUserRepo discordUserRepo,
                         MessagesProvider msg,
                         AuditLogger audit, JDA jda) {
        this.api = api;
        this.accountsRepo = accountsRepo;
        this.profileRepo = profileRepo;
        this.banProgressRepo = banProgressRepo;
        this.discordUserRepo = discordUserRepo;
        this.msg = msg;
        this.audit = audit;
        this.jda = jda;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "link"         -> handleLink(sender, args);
            case "logout"       -> handleLogout(sender, args);
            case "forgive"      -> handleForgive(sender, args);
            case "reload"       -> handleReload(sender);
            case "bypass"       -> handleBypass(sender, args);
            case "lookup"       -> handleLookup(sender, args);
            case "force-relogin" -> handleForceRelogin(sender, args);
            default             -> sendHelp(sender);
        }
        if (audit != null) {
            java.util.Map<String, String> f = new java.util.LinkedHashMap<>();
            f.put("sender", sender.getName());
            f.put("sub", args[0].toLowerCase());
            audit.log("admin", "command", f);
        }
        return true;
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage("§6=== LogDO2 Help ===");
        s.sendMessage("§e/logdo2 link <player_uuid> <discord_id> §7— link player to Discord ID");
        s.sendMessage("§e/logdo2 logout <player_name|player_uuid|discord_id> §7— unlink target");
        s.sendMessage("§e/logdo2 logout <discord_id> <player_name|player_uuid> §7— unlink only that mapping");
        s.sendMessage("§e/logdo2 forgive <ip> §7— clear progressive ban & attempts for IP");
        s.sendMessage("§e/logdo2 bypass <player_name|player_uuid> §7— allow profile to ignore per-Discord limit");
        s.sendMessage("§e/logdo2 lookup <player_name|player_uuid|discord_id> §7— get everything we know about player/member");
        s.sendMessage("§e/logdo2 force-relogin [player_name|player_uuid|discord_id] §7— force re-auth (global requires confirmation)");
        s.sendMessage("§e/logdo2 reload §7— reload config & messages");
    }

    private void handleLookup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("logdo2.admin.lookup")) { noPerm(sender); return; }
        if (args.length < 2) { usage(sender, "lookup <player_name|player_uuid|discord_id>"); return; }

        String target = args[1];

        // Resolve input and get profile
        LogDO2Profile profile;
        UUID resolvedUuid;
        Long resolvedDiscord;
        String resolvedName;

        // ---- Decide what input is and fetch profile ----
        if (isNumeric(target)) {
            // treat as discord id
            resolvedDiscord = Long.parseLong(target);
            profile = api.getProfile(resolvedDiscord);
            // If profile exists, extract primary UUID and name
            if (profile != null && !profile.getLinkInfo().isEmpty()) {
                LinkInfo primary = profile.getLinkInfo().stream()
                        .filter(LinkInfo::isPrimary)
                        .findFirst()
                        .orElseGet(() -> profile.getLinkInfo().getFirst());
                resolvedUuid = primary.getMinecraftProfile().getUuid();
                resolvedName = primary.getMinecraftProfile().getName();
            } else {
                resolvedUuid = null;
                resolvedName = null;
            }
        } else if (isUuid(target)) {
            // treat as UUID
            resolvedUuid = UUID.fromString(target);
            profile = api.getProfile(resolvedUuid);
            if (profile != null) {
                resolvedDiscord = profile.getDiscordProfile().getOAuthInfo().getDiscordId();
                // Find name for this specific UUID
                UUID finalResolvedUuid = resolvedUuid;
                resolvedName = profile.getLinkInfo().stream()
                        .filter(link -> link.getMinecraftProfile().getUuid().equals(finalResolvedUuid))
                        .findFirst()
                        .map(link -> link.getMinecraftProfile().getName())
                        .orElse(null);
            } else {
                resolvedDiscord = null;
                resolvedName = null;
            }
        } else {
            // treat as player name
            resolvedUuid = resolveUuid(target);
            if (resolvedUuid != null) {
                resolvedName = target;
                profile = api.getProfile(resolvedUuid);
                if (profile != null) {
                    resolvedDiscord = profile.getDiscordProfile().getOAuthInfo().getDiscordId();
                } else {
                    resolvedDiscord = null;
                }
            } else {
                profile = null;
                resolvedDiscord = null;
                resolvedName = null;
            }
        }

        // ---- Build single summary output (both plain text and components for players) ----
        StringBuilder outPlain = new StringBuilder();
        List<Component> outComponents = new ArrayList<>();

        outPlain.append("=== Lookup Result ===\n");
        outPlain.append("Query: ").append(target).append("\n");

        outComponents.add(Component.text("=== Lookup Result ===").color(NamedTextColor.GOLD));
        outComponents.add(Component.text("Query: ").append(Component.text(target).color(NamedTextColor.WHITE)));

        if (profile != null && profile.getProfileStatus().equals(LogDO2ProfileStatus.AUTHORIZED)) {
            outComponents.add(buildLabeledCopyComponent("Profile ID: ", String.valueOf(profile.getProfileId())));
        } else {
            outComponents.add(buildLabeledCopyComponent("Profile ID: ", "Not authorized yet..."));
        }

        // If detected Discord id exists
        if (resolvedDiscord != null) {
            outPlain.append("Discord ID: ").append(resolvedDiscord).append("\n");
            outComponents.add(buildLabeledCopyComponent("Discord ID: ", String.valueOf(resolvedDiscord)));

            Optional<String> maybeEmail = discordUserRepo.findEmailByDiscordId(resolvedDiscord);
            if (maybeEmail.isPresent()) {
                outPlain.append("Email: ").append(maskEmail(maybeEmail.get(), 1, 1, '❤')).append("\n");
                outComponents.add(
                        Component.text("Email: ").color(NamedTextColor.GRAY)
                        .append(buildInlineCopyComponent("%s".formatted(maskEmail(maybeEmail.get(), 1, 1, '❤')), maybeEmail.get())));
            }

            List<LinkInfo> linkedProfiles = profile.getLinkInfo();
            if (linkedProfiles.isEmpty()) {
                outPlain.append("  No profiles linked to this Discord.\n");
                outComponents.add(Component.text("  No profiles linked to this Discord.").color(NamedTextColor.GRAY));
            } else {
                outPlain.append("Linked profiles:\n");
                outComponents.add(Component.text("Linked profiles:").color(NamedTextColor.GRAY));
                for (LinkInfo linkInfo : linkedProfiles) {
                    UUID u = linkInfo.getMinecraftProfile().getUuid();
                    String name = linkInfo.getMinecraftProfile().getName();
                    String platform = linkInfo.getMinecraftProfile().getPlatform();

                    outPlain.append(" - ").append(name).append(" (").append(u).append(")\n");

                    // component: clickable line with name + uuid
                    Component line = Component.text(" - ")
                            .append(Component.text(platform != null ? platform : "<unknown>").color(NamedTextColor.DARK_GREEN))
                            .append(Component.space())
                            .append(Component.text(name).color(NamedTextColor.YELLOW))
                            .append(Component.space())
                            .append(buildInlineCopyComponent("(" + u + ")", u.toString()));
                    outComponents.add(line);

                    // linkedAt if available
                    Optional<Long> lat = accountsRepo.linkedAt(u);
                    lat.ifPresent(ts -> {
                        String f = formatEpochSeconds(ts);
                        outPlain.append("    linked at: ").append(f).append("\n");
                        outComponents.add(Component.text("    linked at: ").color(NamedTextColor.GRAY)
                                .append(Component.text(f).color(NamedTextColor.WHITE)));
                    });
                }
            }
        }

        // UUID block
        if (resolvedUuid != null) {
            outPlain.append("Primary UUID: ").append(resolvedUuid).append("\n");
            outComponents.add(buildLabeledCopyComponent("Primary UUID: ", resolvedUuid.toString()));
        } else if (resolvedDiscord == null) {
            outPlain.append("Could not resolve to UUID or Discord ID.\n");
            outComponents.add(Component.text("Could not resolve to UUID or Discord ID.").color(NamedTextColor.RED));
        }

        // Name block
        if (resolvedName != null) {
            outPlain.append("Primary name: ").append(resolvedName).append("\n");
            outComponents.add(Component.text("Primary name: ").color(NamedTextColor.GRAY).append(Component.text(resolvedName).color(NamedTextColor.WHITE)));
        }

        // Linked discord for profile
        if (resolvedDiscord != null) {
            outPlain.append("Linked Discord ID: ").append(resolvedDiscord).append("\n");
            outComponents.add(Component.text("Linked Discord info:").color(NamedTextColor.GRAY));
            // fetch extra member info (string for console)
            String memberInfo = fetchMember(resolvedDiscord);
            outPlain.append(memberInfo);
            // for player: append simplified lines
            outComponents.add(Component.text(memberInfo).color(NamedTextColor.RED));
        } else if (resolvedUuid != null) {
            outPlain.append("No Discord linked/reserved for this profile.\n");
            outComponents.add(Component.text("No Discord linked/reserved for this profile.").color(NamedTextColor.GRAY));
        }

        outPlain.append("--- End of Lookup ---");
        outComponents.add(Component.text("--- End of Lookup ---").color(NamedTextColor.DARK_GRAY));

        // Single send: if player -> components, else console -> plain text
        if (sender instanceof Player p) {
            for (Component c : outComponents) p.sendMessage(c);
        } else {
            sender.sendMessage(outPlain.toString());
        }
        
        // audit log
        if (audit != null) {
            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("sender", sender.getName());
            meta.put("query", target);
            if (resolvedUuid != null) meta.put("uuid", resolvedUuid.toString());
            if (resolvedDiscord != null) meta.put("discord", String.valueOf(resolvedDiscord));
            audit.log("admin", "lookup", meta);
        }
    }

    public static String maskEmail(String email, int keepStart, int keepEnd, char maskChar) {
        if (email == null || email.isBlank()) return "";
        int at = email.indexOf('@');
        if (at <= 0) return email;
        String local = email.substring(0, at);
        String domain = email.substring(at);

        // sanitize keep values
        keepStart = Math.max(0, keepStart);
        keepEnd = Math.max(0, keepEnd);
        if (keepStart + keepEnd >= local.length()) return local + domain;

        StringBuilder sb = new StringBuilder();
        sb.append(local, 0, Math.min(keepStart, local.length()));
        sb.append(String.valueOf(maskChar).repeat(Math.max(0, local.length() - keepStart - keepEnd)));
        if (keepEnd > 0) sb.append(local, Math.max(keepStart, local.length() - keepEnd), local.length());
        sb.append(domain);
        return sb.toString();
    }

    private static boolean isUuid(String s) {
        try { UUID.fromString(s); return true; } catch (IllegalArgumentException e) { return false; }
    }

    /** Formats epoch seconds to dd-MM-yy hh:mm AM/PM in system zone. */
    private static String formatEpochSeconds(long epochSeconds) {
        Instant instant = Instant.ofEpochSecond(epochSeconds);
        ZonedDateTime z = instant.atZone(ZoneId.systemDefault());
        return z.format(LOOKUP_DT);
    }

    /** Helper: build a component that shows label and a clickable copy-to-clipboard value on the same line. */
    private Component buildLabeledCopyComponent(String label, String value) {
        return Component.text(label).color(NamedTextColor.GRAY)
                .append(buildInlineCopyComponent(value, value));
    }

    /** Inline clickable "(value)" component that copies 'copyValue' to clipboard and shows hover. */
    private Component buildInlineCopyComponent(String display, String copyValue) {
        return Component.text(display)
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .hoverEvent(HoverEvent.showText(Component.text("Click to copy")))
                .clickEvent(ClickEvent.copyToClipboard(copyValue));
    }

    private String fetchMember(Long resolvedDiscord) {
        StringBuilder out = new StringBuilder();
        try {
            var guild = jda.getGuildById(Config.getFileConfiguration().getString("discord.targetGuildId", ""));
            if (guild == null) {
                out.append("Unable to find configured Discord guild.\n");
                return out.toString();
            }
            Member dMember = null;
            try {
                dMember = guild.retrieveMemberById(resolvedDiscord).complete();
            } catch (Exception e) {
                log.warn("Unable to fetch user with id {} (not in guild anymore?)", resolvedDiscord);
            }
            if (dMember != null) {
                out.append(" Name: ").append(dMember.getEffectiveName()).append("\n");
                // account creation and join times: convert to ZonedDateTime and format
                Instant created = dMember.getUser().getTimeCreated().toInstant();
                dMember.getTimeJoined();
                Instant joined = dMember.getTimeJoined().toInstant();
                out.append(" Account created: ").append(formatInstant(created)).append("\n");
                out.append(" Joined server: ").append(formatInstant(joined)).append("\n");
            } else {
                out.append(" User is not a member of discord server anymore.\n");
            }
        } catch (Throwable e) {
            out.append(" Unable to get info about Discord profile.\n");
            log.error("Unable to fetch member by user request", e);
        }
        return out.toString();
    }

    private static String formatInstant(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).format(LOOKUP_DT);
    }


    private void handleBypass(CommandSender sender, String[] args) {
        if (!sender.hasPermission("logdo2.admin.bypass")) { noPerm(sender); return; }
        UUID targetUuid;
        if (args.length >= 2) {
            targetUuid = resolveUuid(args[1]);
            if (targetUuid == null) {
                sender.sendMessage("§cUnknown player: " + args[1]);
                return;
            }
        } else if (sender instanceof Player p) {
            targetUuid = p.getUniqueId();
        } else {
            usage(sender, "bypass <player_name|player_uuid>");
            return;
        }

        // Mark one-time bypass in login state
        LoginStateService st = getLoginState();
        if (st == null) {
            sender.sendMessage("§cInternal error: login state not available.");
            return;
        }
        st.grantLimitBypass(targetUuid);
        sender.sendMessage("§aGranted one-time limit bypass for player §e" + targetUuid + "§a.");
        if (audit != null) audit.log("admin", "grant_bypass", java.util.Map.of(
                "sender", sender.getName(),
                "player", targetUuid.toString()
        ));
    }

    private LoginStateService getLoginState() {
        return org.bukkit.Bukkit.getServicesManager().load(LoginStateService.class);
    }

    private void handleLink(CommandSender sender, String[] args) {
        if (!sender.hasPermission("logdo2.admin.link")) { noPerm(sender); return; }
        if (args.length < 3) { usage(sender, "link <player_uuid> <discord_id>"); return; }

        final UUID puuid;
        final long discordId;
        try { puuid = UUID.fromString(args[1]); }
        catch (IllegalArgumentException e) { sender.sendMessage("§cInvalid player UUID."); return; }
        try { discordId = Long.parseLong(args[2]); }
        catch (NumberFormatException e) { sender.sendMessage("§cInvalid Discord ID."); return; }

        // Do not overwrite if any link (reserved or active) already exists for this profile
        if (accountsRepo.findAnyDiscordForProfile(puuid).isPresent()) {
            sender.sendMessage("§cProfile already reserved/linked. Use /logdo2 logout first.");
            return;
        }
        accountsRepo.reserve(discordId, puuid);
        sender.sendMessage("§aReserved profile " + puuid + " for Discord " + discordId + ". Player must authenticate via OAuth.");
        if (audit != null) audit.log("admin", "link_reserve", java.util.Map.of(
                "sender", sender.getName(),
                "player", puuid.toString(),
                "discord", String.valueOf(discordId)
        ));
    }

    private void handleLogout(CommandSender sender, String[] args) {
        if (!sender.hasPermission("logdo2.admin.logout")) { noPerm(sender); return; }
        if (args.length < 2) { usage(sender, "logout <player_name|player_uuid|discord_id> [player_name|player_uuid]"); return; }

        // Case A: two params -> discord + player => unlink only that mapping
        if (args.length >= 3 && isNumeric(args[1])) {
            long discordId = Long.parseLong(args[1]);
            UUID uuid = resolveUuid(args[2]);
            if (uuid == null) { sender.sendMessage("§cUnknown player: " + args[2]); return; }
            accountsRepo.unlinkByDiscordAndProfile(discordId, uuid);
            kickIfOnline(uuid, msg.mc("admin.logout_kick"));
            sender.sendMessage("§aUnlinked Discord " + discordId + " from player " + uuid + ".");
            if (audit != null) audit.log("admin", "logout_pair", java.util.Map.of(
                    "sender", sender.getName(),
                    "discord", String.valueOf(discordId),
                    "player", uuid.toString()
            ));
            return;
        }

        // Case B: single param -> name/uuid/discord
        String target = args[1];

        // name → uuid (offline ok)
        UUID uuidByName = resolveUuid(target);
        if (uuidByName != null) {
            accountsRepo.unlinkByProfile(uuidByName);
            kickIfOnline(uuidByName, msg.mc("admin.logout_kick"));
            sender.sendMessage("§aUnlinked player §e" + target + " §7(" + uuidByName + ")");
            if (audit != null) audit.log("admin", "logout_profile", java.util.Map.of(
                    "sender", sender.getName(),
                    "player", uuidByName.toString()
            ));
            return;
        }

        // uuid →
        try {
            UUID puuid = UUID.fromString(target);
            accountsRepo.unlinkByProfile(puuid);
            kickIfOnline(puuid, msg.mc("admin.logout_kick"));
            sender.sendMessage("§aUnlinked player " + puuid + ".");
            if (audit != null) audit.log("admin", "logout_profile", java.util.Map.of(
                    "sender", sender.getName(),
                    "player", puuid.toString()
            ));
            return;
        } catch (IllegalArgumentException ignore) { }

        // discord id →
        if (isNumeric(target)) {
            long did = Long.parseLong(target);
            // Kick any online players linked to this Discord
            for (UUID u : accountsRepo.findProfilesForDiscord(did)) kickIfOnline(u, msg.mc("admin.logout_kick"));
            accountsRepo.unlinkByDiscord(did);
            sender.sendMessage("§aUnlinked all players for Discord " + did + ".");
            if (audit != null) audit.log("admin", "logout_discord", java.util.Map.of(
                    "sender", sender.getName(),
                    "discord", String.valueOf(did)
            ));
            return;
        }

        sender.sendMessage("§cCan't resolve target. Use player name/uuid or discord id.");
    }

    private static void kickIfOnline(UUID uuid, String reason) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) p.kick(MINI.deserialize(reason));
    }

    private void handleForgive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("logdo2.admin.forgive")) { noPerm(sender); return; }
        if (args.length < 2) { usage(sender, "forgive <ip>"); return; }
        String ip = args[1];

        if (!isValidIp(ip)) {
            sender.sendMessage("§cInvalid IP (IPv4/IPv6) format.");
            return;
        }
        var rec = banProgressRepo.findByIp(ip);
        if (rec.isEmpty()) {
            sender.sendMessage("§eNo record found for IP: §7" + ip);
            return;
        }
        banProgressRepo.reset(ip);
        sender.sendMessage("§aForgave (cleared ban progress) for IP: §e" + ip);
        if (audit != null) audit.log("admin", "forgive_ip", java.util.Map.of(
                "sender", sender.getName(),
                "ip", ip
        ));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("logdo2.admin.reload")) { noPerm(sender); return; }
        Config.reload();
        if (msg instanceof YamlMessages ym) ym.reload();
        Bukkit.getPluginManager().callEvent(new LogDO2ReloadEvent());
        sender.sendMessage("§aLogDO2 reloaded!");
        if (audit != null) audit.log("admin", "reload", java.util.Map.of(
                "sender", sender.getName()
        ));
    }

    private void handleForceRelogin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("logdo2.admin.force-relogin")) { noPerm(sender); return; }

        // Cancel confirmation: /logdo2 force-relogin cancel
        if (args.length == 2 && args[1].equalsIgnoreCase("cancel")) {
            if (pendingForceReloginConfirmations.remove(sender.getName()) != null) {
                sender.sendMessage("§aForce-relogin confirmation cancelled.");
            } else {
                sender.sendMessage("§cYou don't have a pending force-relogin confirmation.");
            }
            return;
        }

        // Global mode: /logdo2 force-relogin (requires confirmation)
        if (args.length == 1) {
            String senderName = sender.getName();
            long now = System.currentTimeMillis();

            // Clean up expired confirmations
            pendingForceReloginConfirmations.entrySet().removeIf(entry ->
                now - entry.getValue() > CONFIRMATION_TIMEOUT_MS
            );

            // Check if sender has a pending confirmation
            Long confirmTimestamp = pendingForceReloginConfirmations.get(senderName);
            if (confirmTimestamp != null && (now - confirmTimestamp) <= CONFIRMATION_TIMEOUT_MS) {
                // Confirmation exists and is valid - proceed with execution
                pendingForceReloginConfirmations.remove(senderName);

                int affected = accountsRepo.markAllForReauth();
                sender.sendMessage("§aMarked " + affected + " Discord account(s) for re-authentication.");
                sender.sendMessage("§eAffected players must complete Discord OAuth on next login.");

                // Kick all online players who need to reauth
                int kicked = 0;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Optional<Long> discordId = accountsRepo.findAnyDiscordForProfile(p.getUniqueId());
                    if (discordId.isPresent() && accountsRepo.requiresReauth(discordId.get())) {
                        kickIfOnline(p.getUniqueId(), msg.mc("admin.force_relogin_kick"));
                        kicked++;
                    }
                }
                sender.sendMessage("§eKicked " + kicked + " online player(s).");

                if (audit != null) audit.log("admin", "force_relogin_all", java.util.Map.of(
                    "sender", sender.getName(),
                    "affected", String.valueOf(affected)
                ));
                return;
            } else {
                // No confirmation or expired - show warning and request confirmation
                pendingForceReloginConfirmations.put(senderName, now);
                sender.sendMessage("§c§l⚠ WARNING ⚠");
                sender.sendMessage("§cYou are about to force §lALL§r§c players to re-authenticate!");
                sender.sendMessage("§cThis will:");
                sender.sendMessage("§c  • Kick all currently online players (" + Bukkit.getOnlinePlayers().size() + " online)");
                sender.sendMessage("§c  • Require every player to complete Discord OAuth again");
                sender.sendMessage("§c  • Affect all linked Discord accounts");
                sender.sendMessage("");
                sender.sendMessage("§eRun the command again within 30 seconds to confirm.");
                sender.sendMessage("§7Type '/logdo2 force-relogin cancel' to abort.");
                sender.sendMessage("§7(Targeted mode: /logdo2 force-relogin <player|discord_id> - no confirmation needed)");
                return;
            }
        }

        // Targeted mode: /logdo2 force-relogin <player_name|player_uuid|discord_id>
        String target = args[1];

        // Try as Discord ID first
        if (isNumeric(target)) {
            long discordId = Long.parseLong(target);
            accountsRepo.markForReauth(discordId);
            sender.sendMessage("§aMarked Discord account " + discordId + " for re-authentication.");

            // Kick affected online players
            int kicked = 0;
            for (UUID uuid : accountsRepo.findProfilesForDiscord(discordId)) {
                kickIfOnline(uuid, msg.mc("admin.force_relogin_kick"));
                kicked++;
            }
            sender.sendMessage("§eKicked " + kicked + " online player(s).");

            if (audit != null) audit.log("admin", "force_relogin_discord", java.util.Map.of(
                "sender", sender.getName(),
                "discord", String.valueOf(discordId),
                "kicked", String.valueOf(kicked)
            ));
            return;
        }

        // Try as player name/UUID
        UUID targetUuid = resolveUuid(target);
        if (targetUuid != null) {
            Optional<Long> discordId = accountsRepo.findAnyDiscordForProfile(targetUuid);
            if (discordId.isEmpty()) {
                sender.sendMessage("§cPlayer " + target + " is not linked to any Discord account.");
                return;
            }

            accountsRepo.markForReauth(discordId.get());
            kickIfOnline(targetUuid, msg.mc("admin.force_relogin_kick"));
            sender.sendMessage("§aMarked player §e" + target + " §7(" + targetUuid + ") for re-authentication.");

            if (audit != null) audit.log("admin", "force_relogin_player", java.util.Map.of(
                "sender", sender.getName(),
                "player", targetUuid.toString(),
                "discord", String.valueOf(discordId.get())
            ));
            return;
        }

        sender.sendMessage("§cCan't resolve target. Use player name/uuid or discord id.");
    }

    // ==== utils ====
    private static void noPerm(CommandSender s) { s.sendMessage("§cYou don't have permission."); }
    private static void usage(CommandSender s, String u) { s.sendMessage("§cUsage: /logdo2 " + u); }
    private static boolean isNumeric(String s) { try { Long.parseLong(s); return true; } catch (Exception e) { return false; } }

    private UUID resolveUuid(String nameOrUuid) {
        // direct UUID
        try { return UUID.fromString(nameOrUuid); } catch (IllegalArgumentException ignore) {}

        // Online player by name
        Player p = Bukkit.getPlayerExact(nameOrUuid);
        if (p != null) return p.getUniqueId();

        // DB (our profiles table)
        var id = profileRepo.findUuidByName(nameOrUuid);
        if (id.isPresent()) return id.get();

        // Offline cache (Paper/Bukkit)
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(nameOrUuid))
                return op.getUniqueId();
        }
        return null;
    }

    private static boolean isValidIp(String ip) {
        try {
            return InetAddress.getByName(ip) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            return SUBS.stream()
                    .filter(sc -> sc.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        // logout targets
        if (args.length == 2 && args[0].equalsIgnoreCase("logout")) {
            List<String> res = new ArrayList<>();
            // online names
            res.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            // UUIDs of online
            res.addAll(Bukkit.getOnlinePlayers().stream().map(p -> p.getUniqueId().toString()).toList());
            // offline cached names (cap to 50)
            int cap = 50;
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.getName() != null) res.add(op.getName());
                if (--cap <= 0) break;
            }
            String pref = args[1].toLowerCase();
            return res.stream()
                    .filter(s -> s.toLowerCase().startsWith(pref))
                    .distinct()
                    .limit(100)
                    .toList();
        }
        // logout <discord_id> <player_name|uuid>
        if (args.length == 3 && args[0].equalsIgnoreCase("logout") && isNumeric(args[1])) {
            List<String> res = new ArrayList<>();
            res.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            res.addAll(Bukkit.getOnlinePlayers().stream().map(p -> p.getUniqueId().toString()).toList());
            int cap = 50;
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.getName() != null) res.add(op.getName());
                if (--cap <= 0) break;
            }
            String pref = args[2].toLowerCase();
            return res.stream().filter(s -> s.toLowerCase().startsWith(pref)).distinct().limit(100).toList();
        }
        // lookup <player_name|uuid|discord_id>
        if (args.length == 2 && args[0].equalsIgnoreCase("lookup")) {
            List<String> res = new ArrayList<>();
            res.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            res.addAll(Bukkit.getOnlinePlayers().stream().map(p -> p.getUniqueId().toString()).toList());
            int cap = 50;
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.getName() != null) res.add(op.getName());
                if (--cap <= 0) break;
            }
            String pref = args[1].toLowerCase();
            return res.stream().filter(s -> s.toLowerCase().startsWith(pref)).distinct().limit(100).toList();
        }

        // bypass <player_name|uuid>
        if (args.length == 2 && args[0].equalsIgnoreCase("bypass")) {
            List<String> res = new ArrayList<>();
            res.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            res.addAll(Bukkit.getOnlinePlayers().stream().map(p -> p.getUniqueId().toString()).toList());
            int cap = 50;
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.getName() != null) res.add(op.getName());
                if (--cap <= 0) break;
            }
            String pref = args[1].toLowerCase();
            return res.stream().filter(s -> s.toLowerCase().startsWith(pref)).distinct().limit(100).toList();
        }
        return Collections.emptyList();
    }
}
