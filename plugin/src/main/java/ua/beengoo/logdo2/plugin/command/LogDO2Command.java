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
import ua.beengoo.logdo2.api.events.LogDO2ReloadEvent;
import ua.beengoo.logdo2.api.ports.*;
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

@Slf4j
public class LogDO2Command implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter LOOKUP_DT = DateTimeFormatter.ofPattern("dd-MM-yy hh:mm a", Locale.ENGLISH);
    private static final List<String> SUBS = List.of("help", "link", "logout", "forgive", "bypass", "reload");
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final AccountsRepo accountsRepo;
    private final ProfileRepo profileRepo;
    private final BanProgressRepo banProgressRepo;
    private final DiscordUserRepo discordUserRepo;
    private final MessagesPort msg;
    private final AuditLogger audit;
    private final JDA jda;

    public LogDO2Command(AccountsRepo accountsRepo,
                         ProfileRepo profileRepo,
                         BanProgressRepo banProgressRepo,
                         DiscordUserRepo discordUserRepo,
                         MessagesPort msg,
                         AuditLogger audit, JDA jda) {
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
            case "link"    -> handleLink(sender, args);
            case "logout"  -> handleLogout(sender, args);
            case "forgive" -> handleForgive(sender, args);
            case "reload"  -> handleReload(sender);
            case "bypass"  -> handleBypass(sender, args);
            case "lookup"  -> handleLookup(sender, args);
            default        -> sendHelp(sender);
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
        s.sendMessage("§e/logdo2 reload §7— reload config & messages");
    }

    private void handleLookup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("logdo2.admin.lookup")) { noPerm(sender); return; }
        if (args.length < 2) { usage(sender, "lookup <player_name|player_uuid|discord_id>"); return; }

        String target = args[1];

        // what we will fill
        UUID resolvedUuid = null;
        Long resolvedDiscord = null;
        String resolvedName = null;

        // auxiliary results to show at the end
        List<UUID> profilesForDiscord = Collections.emptyList();
        Optional<Long> discordForProfile = Optional.empty();

        // ---- Decide what input is (only set variables / collect ids) ----
        if (isNumeric(target)) {
            // treat as discord id
            resolvedDiscord = Long.parseLong(target);
            profilesForDiscord = new ArrayList<>(accountsRepo.findProfilesForDiscord(resolvedDiscord));
            // If there are profiles, pick first to show primary name/uuid
            if (!profilesForDiscord.isEmpty()) {
                UUID first = profilesForDiscord.getFirst();
                resolvedName = profileRepo.findNameByUuid(first).orElse(null);
                resolvedUuid = first;
                discordForProfile = accountsRepo.findAnyDiscordForProfile(first);
            }
        } else if (isUuid(target)) {
            // treat as UUID
            resolvedUuid = UUID.fromString(target);
            resolvedName = profileRepo.findNameByUuid(resolvedUuid).orElse(null);
            discordForProfile = accountsRepo.findAnyDiscordForProfile(resolvedUuid);

            // if profile has a discord, collect all profiles for that discord
            if (discordForProfile.isPresent()) {
                resolvedDiscord = discordForProfile.get();
                profilesForDiscord = new ArrayList<>(accountsRepo.findProfilesForDiscord(resolvedDiscord));
            }
        } else {
            // treat as player name — do not send messages here, just resolve
            resolvedUuid = resolveUuid(target); // may be null
            if (resolvedUuid != null) {
                resolvedName = target;
                discordForProfile = accountsRepo.findAnyDiscordForProfile(resolvedUuid);
                if (discordForProfile.isPresent()) {
                    resolvedDiscord = discordForProfile.get();
                    profilesForDiscord = new ArrayList<>(accountsRepo.findProfilesForDiscord(resolvedDiscord));
                }
            }
        }

        // ---- Build single summary output (both plain text and components for players) ----
        StringBuilder outPlain = new StringBuilder();
        List<Component> outComponents = new ArrayList<>();

        outPlain.append("=== Lookup Result ===\n");
        outPlain.append("Query: ").append(target).append("\n");

        outComponents.add(Component.text("=== Lookup Result ===").color(NamedTextColor.GOLD));
        outComponents.add(Component.text("Query: ").append(Component.text(target).color(NamedTextColor.WHITE)));

        // If detected Discord id
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

            if (profilesForDiscord.isEmpty()) {
                outPlain.append("  No profiles linked to this Discord.\n");
                outComponents.add(Component.text("  No profiles linked to this Discord.").color(NamedTextColor.GRAY));
            } else {
                outPlain.append("Linked profiles:\n");
                outComponents.add(Component.text("Linked profiles:").color(NamedTextColor.GRAY));
                for (UUID u : profilesForDiscord) {
                    String name = profileRepo.findNameByUuid(u).orElse("<unknown>");
                    outPlain.append(" - ").append(name).append(" (").append(u).append(")\n");

                    // component: clickable line with name + uuid
                    Component line = Component.text(" - ")
                            .append(Component.text(profileRepo.findPlatform(u).orElse("<unknown>")).color(NamedTextColor.DARK_GREEN))
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
        } else if (resolvedUuid != null) {
            String maybeName = profileRepo.findNameByUuid(resolvedUuid).orElse(null);
            if (maybeName != null) {
                outPlain.append("Primary name: ").append(maybeName).append("\n");
                outComponents.add(Component.text("Primary name: ").color(NamedTextColor.GRAY).append(Component.text(maybeName).color(NamedTextColor.WHITE)));
            }
        }

        // Linked discord for profile
        Optional<Long> finalDiscord = discordForProfile;
        if (finalDiscord.isPresent()) {
            outPlain.append("Linked Discord ID: ").append(finalDiscord.get()).append("\n");
            outComponents.add(Component.text("Linked Discord info:").color(NamedTextColor.GRAY));
            // fetch extra member info (string for console)
            String memberInfo = fetchMember(finalDiscord.get());
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
        if (keepStart + keepEnd >= local.length()) return local + domain; // нічого маскувати

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
            Member dMember = guild.retrieveMemberById(resolvedDiscord).complete();
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
        ua.beengoo.logdo2.api.ports.LoginStatePort st = getLoginState();
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

    private ua.beengoo.logdo2.api.ports.LoginStatePort getLoginState() {
        return org.bukkit.Bukkit.getServicesManager().load(ua.beengoo.logdo2.api.ports.LoginStatePort.class);
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
        sender.sendMessage("§aLogDO2 configuration reloaded!");
        if (audit != null) audit.log("admin", "reload", java.util.Map.of(
                "sender", sender.getName()
        ));
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
