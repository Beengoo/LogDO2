package ua.beengoo.logdo2.plugin.web;

import io.javalin.Javalin;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import ua.beengoo.logdo2.core.service.LoginService;
import ua.beengoo.logdo2.core.service.ForbiddenLinkException;

import java.util.logging.Logger;

public class LoginEndpoint {
    private final Logger logger;
    private final LoginService loginService;
    private Javalin app;

    public LoginEndpoint(Logger logger, LoginService loginService) {
        this.logger = logger;
        this.loginService = loginService;
    }

    public void start(int port) {
        app = Javalin.create().start(port);
        app.get("/login", this::handleLogin);           // ?state=XYZ
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
            String authUrl = loginService.buildDiscordAuthUrl(state); // валідність перевіряється всередині
            ctx.redirect(authUrl);
        } catch (Exception ex) {
            logger.warning("Login state error: " + ex.getMessage());
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
            ctx.result("Discord account linked. You can return to the game.");
        } catch (ForbiddenLinkException ex) {
            logger.warning("OAuth forbidden: " + ex.getMessage());
            ctx.status(403).result("Forbidden: profile reserved for another Discord account");
        } catch (Exception ex) {
            logger.warning("OAuth callback failed: " + ex.getMessage());
            ctx.status(400).result("OAuth error");
        }
    }
}
