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
    private IpPolicyPort ipPolicy; // optional, provided by other plugins via Services

    private final BanProgressRepo banProgressRepo;
    private final boolean bansEnabled;
    private final long banBaseSec, banMaxSec, banTrackWindowSec;
    private final double banMultiplier;
    private final String banReasonTpl;
    private final int javaLimitPerDiscord;
    private final int bedrockLimitPerDiscord;
    private final boolean limitIncludeReserved;
    private final boolean disallowSimultaneousPlay;

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
                        MessagesPort messages,
                        int javaLimitPerDiscord,
                        int bedrockLimitPerDiscord,
                        boolean limitIncludeReserved,
                        boolean disallowSimultaneousPlay) {
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

        this.javaLimitPerDiscord = Math.max(0, javaLimitPerDiscord);
        this.bedrockLimitPerDiscord = Math.max(0, bedrockLimitPerDiscord);
        this.limitIncludeReserved = limitIncludeReserved;
        this.disallowSimultaneousPlay = disallowSimultaneousPlay;
    }

    public void setDiscordDmPort(DiscordDmPort dm) { this.dm = dm; }
    public void setIpPolicyPort(IpPolicyPort ipPolicy) { this.ipPolicy = ipPolicy; }

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
                showLoginPhaseTitle(uuid);
                sendBedrockHint(uuid, msg.mc("login.bedrock.code_hint", ph));
                firePhaseEnter(uuid, ua.beengoo.logdo2.api.events.LoginPhase.LOGIN);
            } else {
                String token = state.createOAuthState(uuid, currentIp, name, false);
                String loginUrl = publicUrl + "/login?state=" + token;
                showLoginPhaseTitle(uuid);
                sendClickableAuth(uuid,
                        msg.mc("chat.auth_link_text"),
                        msg.mc("chat.auth_link_hover"),
                        loginUrl);
                firePhaseEnter(uuid, ua.beengoo.logdo2.api.events.LoginPhase.LOGIN);
            }
            return;
        }

        String last = profiles.findLastConfirmedIp(uuid).orElse(null);
        if (!Objects.equals(last, currentIp)) {
            long discordId = accounts.findDiscordForProfile(uuid).orElseThrow();
            state.markPendingIpConfirm(uuid, currentIp, discordId);
            if (dm != null) dm.sendIpConfirmDm(discordId, uuid, name, currentIp);
            showIpConfirmPhaseTitle(uuid);
            firePhaseEnter(uuid, ua.beengoo.logdo2.api.events.LoginPhase.IP_CONFIRM);
            return;
        }

        // Simultaneous-play prevention is handled earlier at PlayerLoginEvent to be Folia-safe

        sendActionBar(uuid, msg.mc("login.linked.actionbar"));
    }

    public boolean isActionAllowed(UUID uuid, String currentIp) {
        if (!accounts.isLinked(uuid)) return false;
        if (state.isPendingLogin(uuid)) return false;
        if (state.isPendingIpConfirm(uuid)) return false;
        String last = profiles.findLastConfirmedIp(uuid).orElse(null);
        if (ipPolicy != null && last != null) {
            try {
                return ipPolicy.allow(currentIp, last);
            } catch (Throwable t) {
                // fallback to strict equality on errors
                return Objects.equals(last, currentIp);
            }
        }
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

        // Ensure Discord user exists before creating FK-dependent records
        if (discordUserRepo != null) {
            discordUserRepo.upsertUser(user.id(), user.username(), user.globalName(), user.email(), user.avatar());
        }

        // If profile is already linked/reserved for another Discord user, block with 403
        var existing = accounts.findAnyDiscordForProfile(st.uuid());
        if (existing.isPresent() && existing.get() != user.id()) {
            throw new ForbiddenLinkException("Profile is reserved for a different Discord account");
        }

        // Enforce per-Discord platform limits unless already linked to same discord
        String platform = st.bedrock() ? "BEDROCK" : "JAVA";
        var cur = accounts.findDiscordForProfile(st.uuid());
        if (cur.isEmpty() || cur.get() != user.id()) {
            int limit = st.bedrock() ? bedrockLimitPerDiscord : javaLimitPerDiscord;
            if (limit > 0) {
                int count = accounts.countByDiscordAndPlatform(user.id(), platform, limitIncludeReserved);
                if (count >= limit) {
                    throw new ForbiddenLinkException("Link limit reached for platform " + platform);
                }
            }
        }

        accounts.activate(user.id(), st.uuid());
        tokens.save(user.id(), tokenSet.accessToken(), tokenSet.refreshToken(), tokenSet.expiresAt(),
                tokenSet.tokenType(), tokenSet.scope());
        if (discordUserRepo != null) {
            boolean hasCommands = tokenSet.scope() != null && tokenSet.scope().contains("applications.commands");
            discordUserRepo.setCommandsInstalled(user.id(), hasCommands);
        }

        profiles.updateLastConfirmedIp(st.uuid(), st.ip());
        profiles.updatePlatform(st.uuid(), st.bedrock() ? "BEDROCK" : "JAVA");

        Map<String, String> ph = Map.of("name", st.name());
        sendActionBar(st.uuid(), msg.mc("oauth.linked_actionbar", ph));
        if (dm != null) dm.sendFirstLoginDm(user.id(), st.uuid(), st.name(), publicUrl);
        state.clearPendingLogin(st.uuid());
        clearPhaseTitle(st.uuid());
        firePhaseExit(st.uuid(), ua.beengoo.logdo2.api.events.LoginPhase.LOGIN);
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
        clearPhaseTitle(profileUuid);

        // Fire Bukkit event for integrations (main thread, only if player online)
        runPlayer(profileUuid, p -> {
            try {
                org.bukkit.Bukkit.getPluginManager()
                        .callEvent(new ua.beengoo.logdo2.api.events.PlayerIpConfirmedEvent(p, pending.newIp()));
            } catch (Throwable ignored) {}
        });
        firePhaseExit(profileUuid, ua.beengoo.logdo2.api.events.LoginPhase.IP_CONFIRM);
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
        firePhaseExit(profileUuid, ua.beengoo.logdo2.api.events.LoginPhase.IP_CONFIRM);
    }

    public boolean onDiscordSlashLogin(String code, long discordUserId) {
        var pending = state.consumeOneTimeCode(code);
        if (pending == null) return false;

        // Enforce Bedrock per-Discord limit before reserve
        int limit = bedrockLimitPerDiscord;
        if (limit > 0) {
            int count = accounts.countByDiscordAndPlatform(discordUserId, "BEDROCK", limitIncludeReserved);
            if (count >= limit) return false;
        }

        // Reserve the link, full activation happens after OAuth
        accounts.reserve(discordUserId, pending.uuid());
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
        firePhaseExit(uuid, ua.beengoo.logdo2.api.events.LoginPhase.LOGIN);
    }

    public void onIpConfirmTimeout(UUID uuid) {
        state.consumePendingIpConfirm(uuid);
        kick(uuid, msg.mc("timeouts.ip_kick"));
        firePhaseExit(uuid, ua.beengoo.logdo2.api.events.LoginPhase.IP_CONFIRM);
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
        runPlayer(uuid, p -> {
            Component comp = Component.text(text)
                    .hoverEvent(HoverEvent.showText(Component.text(hover)))
                    .clickEvent(ClickEvent.openUrl(loginUrl));
            p.sendMessage(comp);
        });
    }

    private void sendBedrockHint(UUID uuid, String line) {
        runPlayer(uuid, p -> p.sendMessage(line));
    }

    private void sendTitle(UUID uuid, String title, String subtitle, int in, int stay, int out) {
        runPlayer(uuid, p -> p.sendTitle(title, subtitle, in, stay, out));
    }

    public void showLoginPhaseTitle(UUID uuid) {
        sendTitle(uuid, msg.mc("login.first_join.title"), msg.mc("login.first_join.subtitle"), 0, 5000, 0);
    }

    public void showIpConfirmPhaseTitle(UUID uuid) {
        sendTitle(uuid, msg.mc("ip.unconfirmed.title"), msg.mc("ip.unconfirmed.subtitle"), 0, 5000, 0);
    }

    public void clearPhaseTitle(UUID uuid) {
        runPlayer(uuid, p -> {
            try { p.clearTitle(); } catch (Throwable ignored) { /* older API fallback */ }
            try { p.resetTitle(); } catch (Throwable ignored) { /* older API fallback */ }
        });
    }

    private void sendActionBar(UUID uuid, String msgLine) {
        runPlayer(uuid, p -> p.sendActionBar(msgLine));
    }

    private void kick(UUID uuid, String reason) {
        // Folia-safe: perform kick on player's region thread with a 1-tick delay
        try {
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    try {
                        p.getScheduler().execute(plugin, () -> p.kickPlayer(reason), null, 1L);
                    } catch (Throwable ignored) {
                        // Non-Folia Paper path: kick immediately
                        p.kickPlayer(reason);
                    }
                }
            });
        } catch (Throwable ignored) {
            // Legacy Bukkit fallback: schedule 1 tick later
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.kickPlayer(reason);
            }, 1L);
        }
    }

    // Check at login time whether the player should be disallowed (e.g., simultaneous play)
    public Optional<String> disallowReasonOnLogin(UUID uuid, String name, String currentIp, boolean bedrock) {
        if (!disallowSimultaneousPlay) return Optional.empty();
        var owner = accounts.findDiscordForProfile(uuid);
        if (owner.isEmpty()) return Optional.empty();
        long discordId = owner.get();
        for (org.bukkit.entity.Player other : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(uuid)) continue;
            var otherOwner = accounts.findDiscordForProfile(other.getUniqueId());
            if (otherOwner.isPresent() && otherOwner.get() == discordId) {
                java.util.Map<String, String> ph = java.util.Map.of("other", other.getName());
                return Optional.of(msg.mc("limits.simultaneous_kick", ph));
            }
        }
        return Optional.empty();
    }

    private void runMain(Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
            return;
        }
        // Prefer Folia/Paper schedulers if available
        try {
            // Paper API on both Paper and Folia
            Bukkit.getGlobalRegionScheduler().execute(plugin, r);
        } catch (Throwable ignored) {
            // Fallback to legacy Bukkit scheduler (non-Folia)
            try {
                Bukkit.getScheduler().runTask(plugin, r);
            } catch (Throwable t) {
                throw new UnsupportedOperationException("No compatible scheduler available", t);
            }
        }
    }

    private void runPlayer(UUID uuid, java.util.function.Consumer<Player> action) {
        if (Bukkit.isPrimaryThread()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) action.accept(p);
            return;
        }
        try {
            // Resolve player on the global scheduler, then switch to player scheduler
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    try {
                        p.getScheduler().execute(plugin, () -> action.accept(p), null, 0L);
                    } catch (Throwable ignored) {
                        // If player scheduler is not available, run action immediately (Paper non-Folia)
                        action.accept(p);
                    }
                }
            });
        } catch (Throwable ignored) {
            // Non-Folia fallback
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) action.accept(p);
            });
        }
    }

    private void firePhaseEnter(UUID uuid, ua.beengoo.logdo2.api.events.LoginPhase phase) {
        runPlayer(uuid, p -> {
            try {
                org.bukkit.Bukkit.getPluginManager()
                        .callEvent(new ua.beengoo.logdo2.api.events.PlayerLoginPhaseEnterEvent(p, phase));
            } catch (Throwable ignored) {}
        });
    }

    private void firePhaseExit(UUID uuid, ua.beengoo.logdo2.api.events.LoginPhase phase) {
        runPlayer(uuid, p -> {
            try {
                org.bukkit.Bukkit.getPluginManager()
                        .callEvent(new ua.beengoo.logdo2.api.events.PlayerLoginPhaseExitEvent(p, phase));
            } catch (Throwable ignored) {}
        });
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
