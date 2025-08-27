package ua.beengoo.logdo2.core.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ua.beengoo.logdo2.api.ports.*;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class LoginService {
    private final OAuthPort oauth;
    private DiscordDmPort dm;
    private final AccountsRepo accounts;
    private final ProfileRepo profiles;
    private final TokensRepo tokens;
    private final LoginStatePort state;
    private final DiscordUserRepo discordUserRepo;
    private final Logger log;
    private final String publicUrl;
    private final String redirectUri;
    private final Plugin plugin;
    private final MessagesPort msg;

    private final BanProgressRepo banProgressRepo;
    private final boolean bansEnabled;
    private final long banBaseSec, banMaxSec, banTrackWindowSec;
    private final double banMultiplier;
    private final String banReasonTpl;

    public LoginService(OAuthPort oauth, DiscordDmPort dm,
                        AccountsRepo accounts, ProfileRepo profiles, TokensRepo tokens,
                        LoginStatePort state, Logger log,
                        String publicUrl, String redirectUri,
                        DiscordUserRepo discordUserRepo,
                        Plugin plugin,
                        BanProgressRepo banProgressRepo,
                        boolean bansEnabled,
                        long banBaseSec, double banMultiplier,
                        long banMaxSec, long banTrackWindowSec,
                        String banReasonTpl,
                        MessagesPort messages) {
        this.oauth = oauth;
        this.dm = dm;
        this.accounts = accounts;
        this.profiles = profiles;
        this.tokens = tokens;
        this.state = state;
        this.log = log;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length()-1) : publicUrl;
        this.redirectUri = redirectUri;
        this.discordUserRepo = discordUserRepo;
        this.plugin = plugin;
        this.msg = messages;

        this.banProgressRepo = banProgressRepo;
        this.bansEnabled = bansEnabled;
        this.banBaseSec = Math.max(1L, banBaseSec);
        this.banMultiplier = banMultiplier <= 0 ? 2.0 : banMultiplier;
        this.banMaxSec = Math.max(this.banBaseSec, banMaxSec);
        this.banTrackWindowSec = Math.max(0L, banTrackWindowSec);
        this.banReasonTpl = (banReasonTpl == null || banReasonTpl.isBlank())
                ? "Suspicious login attempt. Ban: %DURATION%."
                : banReasonTpl;
    }

    public void setDiscordDmPort(DiscordDmPort dm) { this.dm = dm; }

    // === Join flow ===
    public void onPlayerJoin(UUID uuid, String name, String currentIp, boolean bedrock) {
        profiles.upsertName(uuid, name);
        profiles.updatePlatform(uuid, bedrock ? "BEDROCK" : "JAVA");

        // страховка якщо PreLogin не спрацював
        long now = System.currentTimeMillis() / 1000;
        var rec = banProgressRepo.findByIp(currentIp);
        if (rec.isPresent() && rec.get().lastBanUntilEpochSec() > now) {
            long remain = rec.get().lastBanUntilEpochSec() - now;
            Map<String, String> phb = Map.of("remaining", humanDuration(remain));
            kick(uuid, msg.mc("prelogin.banned", phb));
            return;
        }

        if (!accounts.isLinked(uuid)) {
            state.markPendingLogin(uuid, currentIp, bedrock);
            if (bedrock) {
                String code = state.createOneTimeCode(uuid, currentIp, name);
                Map<String, String> ph = Map.of("code", code);
                sendTitle(uuid,
                        msg.mc("login.first_join.title"),
                        msg.mc("login.first_join.subtitle"),
                        10, 80, 10);
                sendBedrockHint(uuid, msg.mc("login.bedrock.code_hint", ph));
            } else {
                String token = state.createOAuthState(uuid, currentIp, name, false);
                String loginUrl = publicUrl + "/login?state=" + token;
                sendTitle(uuid,
                        msg.mc("login.first_join.title"),
                        msg.mc("login.first_join.subtitle"),
                        10, 80, 10);
                sendClickableAuth(uuid,
                        msg.mc("chat.auth_link_text"),
                        msg.mc("chat.auth_link_hover"),
                        loginUrl);
            }
            return;
        }

        String last = profiles.findLastConfirmedIp(uuid).orElse(null);
        if (!Objects.equals(last, currentIp)) {
            long discordId = accounts.findDiscordForProfile(uuid).orElseThrow();
            state.markPendingIpConfirm(uuid, currentIp, discordId);
            if (dm != null) dm.sendIpConfirmDm(discordId, uuid, name, currentIp);
            sendTitle(uuid, msg.mc("ip.unconfirmed.title"), msg.mc("ip.unconfirmed.subtitle"), 10, 80, 10);
            return;
        }

        sendActionBar(uuid, msg.mc("login.linked.actionbar"));
    }

    public boolean isActionAllowed(UUID uuid, String currentIp) {
        if (!accounts.isLinked(uuid)) return false;
        if (state.isPendingLogin(uuid)) return false;
        if (state.isPendingIpConfirm(uuid)) return false;
        String last = profiles.findLastConfirmedIp(uuid).orElse(null);
        return Objects.equals(last, currentIp);
    }

    public String buildDiscordAuthUrl(String stateToken) {
        if (!state.hasOAuthState(stateToken))
            throw new IllegalStateException("Unknown or expired login state");
        return oauth.buildAuthUrl(stateToken, redirectUri);
    }

    public String createOAuthState(UUID uuid, String ip, String name, boolean bedrock) {
        return state.createOAuthState(uuid, ip, name, bedrock);
    }

    public void onOAuthCallback(String code, String stateToken) {
        var st = state.consumeOAuthState(stateToken);
        var tokenSet = oauth.exchangeCode(code, redirectUri);
        var user = oauth.fetchUser(tokenSet.accessToken());

        accounts.link(user.id(), st.uuid());
        tokens.save(user.id(), tokenSet.accessToken(), tokenSet.refreshToken(), tokenSet.expiresAt(),
                tokenSet.tokenType(), tokenSet.scope());
        if (discordUserRepo != null) {
            discordUserRepo.upsertUser(user.id(), user.username(), user.globalName(), user.email(), user.avatar());
            boolean hasCommands = tokenSet.scope() != null && tokenSet.scope().contains("applications.commands");
            discordUserRepo.setCommandsInstalled(user.id(), hasCommands);
        }

        profiles.updateLastConfirmedIp(st.uuid(), st.ip());
        profiles.updatePlatform(st.uuid(), st.bedrock() ? "BEDROCK" : "JAVA");

        Map<String, String> ph = Map.of("name", st.name());
        sendActionBar(st.uuid(), msg.mc("oauth.linked_actionbar", ph));
        if (dm != null) dm.sendFirstLoginDm(user.id(), st.uuid(), st.name(), publicUrl);
        state.clearPendingLogin(st.uuid());
    }

    public void onDiscordIpConfirm(UUID profileUuid, long discordUserId) {
        Optional<Long> owner = accounts.findDiscordForProfile(profileUuid);
        if (owner.isEmpty() || owner.get() != discordUserId) {
            log.warning("IP confirm by non-owner. profile=" + profileUuid + " by " + discordUserId);
            return;
        }
        var pending = state.consumePendingIpConfirm(profileUuid);
        if (pending == null) return;

        profiles.updateLastConfirmedIp(profileUuid, pending.newIp());
        sendActionBar(profileUuid, msg.mc("ip.confirm_actionbar"));
    }

    public void onDiscordIpReject(UUID profileUuid, long discordUserId) {
        Optional<Long> owner = accounts.findDiscordForProfile(profileUuid);
        if (owner.isEmpty() || owner.get() != discordUserId) {
            log.warning("IP reject by non-owner. profile=" + profileUuid + " by " + discordUserId);
            return;
        }
        var pending = state.consumePendingIpConfirm(profileUuid);
        if (pending == null) return;

        long durSec = applyProgressiveBan(pending.newIp());
        Map<String, String> ph = Map.of("duration", humanDuration(durSec));
        sendActionBar(profileUuid, msg.mc("ip.reject_actionbar", ph));
        kick(profileUuid, msg.mc("ip.reject_kick", ph));
    }

    public boolean onDiscordSlashLogin(String code, long discordUserId) {
        var pending = state.consumeOneTimeCode(code);
        if (pending == null) return false;

        accounts.link(discordUserId, pending.uuid());
        profiles.updateLastConfirmedIp(pending.uuid(), pending.ip());
        profiles.updatePlatform(pending.uuid(), "BEDROCK");

        String token = state.createOAuthState(pending.uuid(), pending.ip(), pending.name(), true);
        String loginUrl = publicUrl + "/login?state=" + token;
        if (dm != null) dm.sendFinalizeOAuthLink(discordUserId, loginUrl);

        return true;
    }

    public void onLoginTimeout(UUID uuid) {
        state.clearPendingLogin(uuid);
        kick(uuid, msg.mc("timeouts.login_kick"));
    }

    public void onIpConfirmTimeout(UUID uuid) {
        state.consumePendingIpConfirm(uuid);
        kick(uuid, msg.mc("timeouts.ip_kick"));
    }

    // === Progressive bans (only ban_progress table) ===
    private long applyProgressiveBan(String ip) {
        if (!bansEnabled || ip == null || ip.isBlank()) return 0L;

        long now = System.currentTimeMillis() / 1000;
        var recOpt = banProgressRepo.findByIp(ip);
        int attempts = 0;
        long lastAttempt = 0;

        if (recOpt.isPresent()) {
            var rec = recOpt.get();
            attempts = rec.attempts();
            lastAttempt = rec.lastAttemptEpochSec();
            if (banTrackWindowSec > 0 && now - lastAttempt > banTrackWindowSec) {
                attempts = 0;
            }
        }

        attempts += 1;
        double pow = Math.pow(banMultiplier, Math.max(0, attempts - 1));
        long dur = (long) Math.floor(banBaseSec * pow);
        if (dur > banMaxSec) dur = banMaxSec;

        long untilSec = now + dur;
        banProgressRepo.upsert(ip, attempts, now, untilSec);
        return dur;
    }

    // === UI & main-thread helpers ===
    private void sendClickableAuth(UUID uuid, String text, String hover, String loginUrl) {
        runMain(() -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) return;
            Component comp = Component.text(text)
                    .hoverEvent(HoverEvent.showText(Component.text(hover)))
                    .clickEvent(ClickEvent.openUrl(loginUrl));
            p.sendMessage(comp);
        });
    }

    private void sendBedrockHint(UUID uuid, String line) {
        runMain(() -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(line);
        });
    }

    private void sendTitle(UUID uuid, String title, String subtitle, int in, int stay, int out) {
        runMain(() -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendTitle(title, subtitle, in, stay, out);
        });
    }

    private void sendActionBar(UUID uuid, String msgLine) {
        runMain(() -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendActionBar(msgLine);
        });
    }

    private void kick(UUID uuid, String reason) {
        runMain(() -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.kickPlayer(reason);
        });
    }

    private void runMain(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }

    private static String humanDuration(long seconds) {
        long s = seconds;
        long d = s / 86400; s %= 86400;
        long h = s / 3600;  s %= 3600;
        long m = s / 60;    s %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (d == 0 && h == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }
}
