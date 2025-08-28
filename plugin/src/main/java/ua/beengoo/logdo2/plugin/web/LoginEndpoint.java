package ua.beengoo.logdo2.plugin.web;

import io.javalin.Javalin;
import io.javalin.http.Context;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.requests.restaction.InviteAction;
import org.jetbrains.annotations.NotNull;
import ua.beengoo.logdo2.core.service.LoginService;
import ua.beengoo.logdo2.core.service.ForbiddenLinkException;

import java.util.logging.Logger;

public class LoginEndpoint {
    private final Logger logger;
    private final LoginService loginService;
    private final JDA jda;
    private final String postAction;
    private final String postText;
    private final String redirectUrl;
    private final String targetGuildId;
    private final String inviteChannelId;
    private Javalin app;
    private final ua.beengoo.logdo2.plugin.util.AuditLogger audit;

    public LoginEndpoint(Logger logger, LoginService loginService,
                         JDA jda,
                         String postAction,
                         String postText,
                         String redirectUrl,
                         String targetGuildId,
                         String inviteChannelId,
                         ua.beengoo.logdo2.plugin.util.AuditLogger audit) {
        this.logger = logger;
        this.loginService = loginService;
        this.jda = jda;
        this.postAction = postAction == null ? "text" : postAction.trim().toLowerCase();
        this.postText = postText;
        this.redirectUrl = redirectUrl;
        this.targetGuildId = targetGuildId;
        this.inviteChannelId = inviteChannelId;
        this.audit = audit;
    }

    public void start(int port) {
        app = Javalin.create().start(port);
        app.get("/login", this::handleLogin);
        app.get("/oauth/callback", this::handleCallback);
        logger.info("LoginEndpoint started on " + port);
    }

    public void stop() {
        if (app != null) app.stop();
    }

    private void handleLogin(@NotNull Context ctx) {
        String state = ctx.queryParam("state");
        if (state == null || state.isBlank()) {
            ctx.status(400).result("Missing state");
            return;
        }

        try {
            String authUrl = loginService.buildDiscordAuthUrl(state);
            if (audit != null) audit.log("web", "login_start", java.util.Map.of(
                    "state", state
            ));
            ctx.redirect(authUrl);
        } catch (Exception ex) {
            logger.warning("Login state error: " + ex.getMessage());
            if (audit != null) audit.log("web", "login_start_error", java.util.Map.of(
                    "state", state,
                    "error", ex.getMessage() == null ? "error" : ex.getMessage()
            ));
            ctx.status(400).result("Invalid or expired login state");
        }
    }

    private void handleCallback(@NotNull Context ctx) {
        String code  = ctx.queryParam("code");
        String state = ctx.queryParam("state");
        if (code == null || state == null) {
            ctx.status(400).result("Missing code/state");
            return;
        }
        try {
            loginService.onOAuthCallback(code, state);
            if (audit != null) audit.log("web", "oauth_callback_ok", java.util.Map.of(
                    "state", state
            ));
            switch (postAction) {
                case "redirect":
                    if (redirectUrl == null || redirectUrl.isBlank()) {
                        ctx.result(postText != null && !postText.isBlank()
                                ? postText
                                : "Login complete. You can return to the game.");
                    } else {
                        if (audit != null) audit.log("web", "post_redirect", java.util.Map.of("url", redirectUrl));
                        ctx.redirect(redirectUrl);
                    }
                    break;
                case "discord-invite":
                    handleDiscordInvite(ctx);
                    break;
                case "text":
                default:
                    ctx.result(postText != null && !postText.isBlank()
                            ? postText
                            : "Discord account linked. You can return to the game.");
            }
        } catch (ForbiddenLinkException ex) {
            logger.warning("OAuth forbidden: " + ex.getMessage());
            if (audit != null) audit.log("web", "oauth_callback_forbidden", java.util.Map.of(
                    "state", state,
                    "error", ex.getMessage() == null ? "forbidden" : ex.getMessage()
            ));
            ctx.status(403).result("Forbidden: profile reserved for another Discord account");
        } catch (Exception ex) {
            logger.warning("OAuth callback failed: " + ex.getMessage());
            if (audit != null) audit.log("web", "oauth_callback_error", java.util.Map.of(
                    "state", state,
                    "error", ex.getMessage() == null ? "error" : ex.getMessage()
            ));
            ctx.status(400).result("OAuth error");
        }
    }

    private void handleDiscordInvite(@NotNull Context ctx) {
        if (jda == null) {
            ctx.status(500).result("Discord not initialized");
            return;
        }
        if (targetGuildId == null || targetGuildId.isBlank() || inviteChannelId == null || inviteChannelId.isBlank()) {
            ctx.status(500).result("Invite config missing: targetGuildId/inviteChannelId");
            return;
        }
        Guild guild = jda.getGuildById(targetGuildId);
        if (guild == null) {
            ctx.status(500).result("Guild not found: " + targetGuildId);
            return;
        }
        TextChannel ch = guild.getTextChannelById(inviteChannelId);
        if (ch == null) {
            ctx.status(500).result("Invite channel not found: " + inviteChannelId);
            return;
        }
        try {
            String url = null;
            // Always attempt to reuse an existing invite created by this bot
            try {
                var invites = ch.retrieveInvites().complete();
                var selfId = jda.getSelfUser().getIdLong();
                for (Invite inv : invites) {
                    if (inv.isExpanded() && inv.getInviter() != null && inv.getInviter().getIdLong() == selfId) {
                        url = inv.getUrl();
                        break;
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to retrieve existing invites: " + e.getMessage());
            }

            if (url == null) {
                InviteAction action = ch.createInvite()
                        .setTemporary(false)
                        .setMaxAge(0)
                        .setMaxUses(0);
                Invite inv = action.complete();
                url = inv.getUrl();
            }
            if (audit != null) audit.log("web", "post_discord_invite", java.util.Map.of(
                    "guild", targetGuildId,
                    "channel", inviteChannelId,
                    "url", url
            ));
            ctx.redirect(url);
        } catch (Exception e) {
            logger.warning("Failed to create/retrieve invite: " + e.getMessage());
            if (audit != null) audit.log("web", "post_discord_invite_error", java.util.Map.of(
                    "guild", targetGuildId,
                    "channel", inviteChannelId,
                    "error", e.getMessage() == null ? "error" : e.getMessage()
            ));
            ctx.status(500).result("Failed to create invite");
        }
    }
}
