package ua.beengoo.logdo2.plugin.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ua.beengoo.logdo2.api.ports.AccountsRepo;
import ua.beengoo.logdo2.api.ports.BanProgressRepo;
import ua.beengoo.logdo2.api.ports.MessagesPort;
import ua.beengoo.logdo2.api.ports.ProfileRepo;
import ua.beengoo.logdo2.core.service.LoginService;

import java.net.InetAddress;
import java.util.*;
import java.util.stream.Collectors;

public class LogDO2Command implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("help", "link", "logout", "forgive", "reload");

    private final LoginService loginService;
    private final AccountsRepo accountsRepo;
    private final ProfileRepo profileRepo;
    private final BanProgressRepo banProgressRepo;
    private final MessagesPort msg;

    public LogDO2Command(LoginService loginService,
                         AccountsRepo accountsRepo,
                         ProfileRepo profileRepo,
                         BanProgressRepo banProgressRepo,
                         MessagesPort msg) {
        this.loginService = loginService;
        this.accountsRepo = accountsRepo;
        this.profileRepo = profileRepo;
        this.banProgressRepo = banProgressRepo;
        this.msg = msg;
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
            default        -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage("§6=== LogDO2 Help ===");
        s.sendMessage("§e/logdo2 link <player_uuid> <discord_id> §7— link player to Discord ID");
        s.sendMessage("§e/logdo2 logout <player_name|player_uuid|discord_id> §7— unlink target");
        s.sendMessage("§e/logdo2 logout <discord_id> <player_name|player_uuid> §7— unlink only that mapping");
        s.sendMessage("§e/logdo2 forgive <ip> §7— clear progressive ban & attempts for IP");
        s.sendMessage("§e/logdo2 reload §7— reload config & messages");
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
            return;
        }

        // uuid →
        try {
            UUID puuid = UUID.fromString(target);
            accountsRepo.unlinkByProfile(puuid);
            kickIfOnline(puuid, msg.mc("admin.logout_kick"));
            sender.sendMessage("§aUnlinked player " + puuid + ".");
            return;
        } catch (IllegalArgumentException ignore) { }

        // discord id →
        if (isNumeric(target)) {
            long did = Long.parseLong(target);
            // Kick any online players linked to this Discord
            for (UUID u : accountsRepo.findProfilesForDiscord(did)) kickIfOnline(u, msg.mc("admin.logout_kick"));
            accountsRepo.unlinkByDiscord(did);
            sender.sendMessage("§aUnlinked all players for Discord " + did + ".");
            return;
        }

        sender.sendMessage("§cCan't resolve target. Use player name/uuid or discord id.");
    }

    private static void kickIfOnline(UUID uuid, String reason) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) p.kickPlayer(reason);
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
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("logdo2.admin.reload")) { noPerm(sender); return; }
        var pl = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("LogDO2"));
        pl.reloadConfig();
        if (pl instanceof ua.beengoo.logdo2.plugin.LogDO2 main) {
            main.updateConfigDefaults(); // merge any new defaults into existing file
        }
        if (msg instanceof ua.beengoo.logdo2.plugin.i18n.YamlMessages ym) ym.reload();
        sender.sendMessage("§aLogDO2 configuration reloaded!");
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
        try { InetAddress.getByName(ip); return true; } catch (Exception e) { return false; }
    }

    // ==== tab complete ====
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
        return Collections.emptyList();
    }
}
