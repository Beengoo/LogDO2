package ua.beengoo.logdo2.plugin.web;

import io.javalin.Javalin;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.requests.restaction.InviteAction;
import org.jetbrains.annotations.NotNull;
import ua.beengoo.logdo2.core.service.LoginService;
import ua.beengoo.logdo2.core.service.ForbiddenLinkException;
import ua.beengoo.logdo2.plugin.util.AuditLogger;

import java.util.Map;

@Slf4j(topic = "LogDO2")
public class HttpLoginServer {
    private final LoginService loginService;
    private final JDA jda;
    private final String postAction;
    private final String postText;
    private final String redirectUrl;
    private final String targetGuildId;
    private final String inviteChannelId;
    private Javalin app;
    private final AuditLogger audit;

    public HttpLoginServer(LoginService loginService,
                           JDA jda,
                           String postAction,
                           String postText,
                           String redirectUrl,
                           String targetGuildId,
                           String inviteChannelId,
                           AuditLogger audit) {
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
        app = Javalin.create(javalinConfig -> javalinConfig.showJavalinBanner = false).start(port);
        app.get("/login", this::handleLogin);
        app.get("/oauth/callback", this::handleCallback);
        log.info("Running web server on port: {}", port);
    }

    public void stop() {
        if (app != null) app.stop();
    }

    public void restart(int port){
        if (app != null && app.port() != port) {
            app.stop();
            start(port);
        }
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
            log.warn("Login state error: " + ex.getMessage());
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
            loginService.handleWebServerCallback(code, state);
            if (audit != null) audit.log("web", "oauth_callback_ok", Map.of(
                    "state", state
            ));
            switch (postAction) {
                case "redirect":
                    if (redirectUrl == null || redirectUrl.isBlank()) {
                        ctx.result(postText != null && !postText.isBlank()
                                ? postText
                                : "Login complete. You can return to the game.");
                    } else {
                        if (audit != null) audit.log("web", "post_redirect", Map.of("url", redirectUrl));
                        ctx.redirect(redirectUrl);
                    }
                    break;
                case "discord-invite":{
                    if (!handleDiscordInvite(ctx)) {
                        handleText(ctx);
                    }
                    break;
                }
                case "text": handleText(ctx);
                default: handleText(ctx);

            }
        } catch (ForbiddenLinkException ex) {
            if (audit != null) audit.log("web", "oauth_callback_forbidden", Map.of(
                    "state", state,
                    "error", ex.getMessage() == null ? "forbidden" : ex.getMessage()
            ));
            ctx.status(403).result("Forbidden action: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error on login handle", ex);
            if (audit != null) audit.log("web", "oauth_callback_error", Map.of(
                    "state", state,
                    "error", ex.getMessage() == null ? "error" : ex.getMessage()
            ));
            ctx.status(400).result("Internal server error, try again later.");
        }
    }

    private void handleText(Context ctx){
        ctx.result(postText != null && !postText.isBlank()
                ? postText
                : "Discord account linked. You can return to the game.");
    }

    private boolean handleDiscordInvite(@NotNull Context ctx) {
        if (jda == null) {
            log.warn("Trying to get invite link while discord is not ready yet!");
            return false;
        }
        if (targetGuildId == null || targetGuildId.isBlank() || inviteChannelId == null || inviteChannelId.isBlank()) {
            log.warn("Invite configuration missing: discord.targetGuildId and discord.inviteChannelId");
            return false;
        }
        Guild guild = jda.getGuildById(targetGuildId);
        if (guild == null) {
            log.warn("Guild provided in discord.targetGuildId not found!");
            return false;
        }
        TextChannel ch = guild.getTextChannelById(inviteChannelId);
        if (ch == null) {
            log.warn("Invite channel provided in discord.inviteChannelId not found!");
            return false;
        }
        try {
            String url = null;
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
                log.warn("Unable to retrieve existing invite URL", e);
                return false;
            }

            if (url == null) {
                InviteAction action = ch.createInvite()
                        .setTemporary(false)
                        .setMaxAge(0)
                        .setMaxUses(0);
                Invite inv = action.complete();
                url = inv.getUrl();
            }
            ctx.redirect(url);
            if (audit != null) audit.log("web", "post_discord_invite", Map.of(
                    "guild", targetGuildId,
                    "channel", inviteChannelId,
                    "url", url
            ));
            return true;
        } catch (Exception e) {
            log.warn("Unable to retrieve invite link", e);
            if (audit != null) audit.log("web", "post_discord_invite_error", Map.of(
                    "guild", targetGuildId,
                    "channel", inviteChannelId,
                    "error", e.getMessage() == null ? "error" : e.getMessage()
            ));
            log.warn("Failed to create invite link!");
            return false;
        }
    }
}
