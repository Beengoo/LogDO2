package ua.beengoo.logdo2.plugin;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import ua.beengoo.logdo2.api.ports.*;
import ua.beengoo.logdo2.core.service.LoginService;
import ua.beengoo.logdo2.core.service.LoginStateService;
import ua.beengoo.logdo2.plugin.adapters.discord.JdaDiscordDmAdapter;
import ua.beengoo.logdo2.plugin.adapters.jdbc.*;
import ua.beengoo.logdo2.plugin.adapters.oauth.DiscordOAuthAdapter;
import ua.beengoo.logdo2.plugin.command.LogDO2Command;
import ua.beengoo.logdo2.plugin.db.DatabaseManager;
import ua.beengoo.logdo2.plugin.discord.JdaDiscordButtonListener;
import ua.beengoo.logdo2.plugin.discord.JdaSlashLoginListener;
import ua.beengoo.logdo2.plugin.discord.SlashCommandRegistrar;
import ua.beengoo.logdo2.plugin.i18n.YamlMessages;
import ua.beengoo.logdo2.plugin.listeners.PlayerListener;
import ua.beengoo.logdo2.plugin.listeners.PreLoginListener;
import ua.beengoo.logdo2.plugin.integration.FloodgateHook;
import ua.beengoo.logdo2.plugin.runtime.TimeoutManager;
import ua.beengoo.logdo2.plugin.util.TokenCrypto;
import ua.beengoo.logdo2.plugin.web.LoginEndpoint;

import java.time.Duration;
import java.util.Objects;

public final class LogDO2 extends JavaPlugin {
    private JDA jda;
    private LoginEndpoint loginEndpoint;
    private TimeoutManager timeouts;

    // Ports/adapters
    private OAuthPort oauthPort;
    private DiscordDmPort discordDmPort;
    private AccountsRepo accountsRepo;
    private ProfileRepo profileRepo;
    private TokensRepo tokensRepo;
    private YamlMessages messages;

    private DiscordUserRepo discordUserRepo;
    private BanProgressRepo banProgressRepo;
    private LoginStatePort loginStatePort;

    private DatabaseManager db;
    private LoginService loginService;
    private FloodgateHook floodgate;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.messages = new YamlMessages(this);

        int    webPort      = getConfig().getInt("web.port", 8080);
        String publicUrl    = stripTrailingSlash(getConfig().getString("web.publicUrl", "http://localhost:" + webPort));
        String botToken     = getConfig().getString("discord.botToken", "");
        String clientId     = getConfig().getString("oauth.clientId", "");
        String clientSecret = getConfig().getString("oauth.clientSecret", "");
        String scopes       = getConfig().getString("oauth.scopes", "identify email applications.commands");

        long loginSec  = getConfig().getLong("timeouts.loginSeconds", 300L);
        long ipConfSec = getConfig().getLong("timeouts.ipConfirmSeconds", 180L);

        boolean bansEnabled = getConfig().getBoolean("bans.enabled", true);
        long    baseSec     = getConfig().getLong("bans.baseSeconds", 1800L);
        double  mult        = getConfig().getDouble("bans.multiplier", 2.0);
        long    maxSec      = getConfig().getLong("bans.maxSeconds", 604800L);
        long    windowSec   = getConfig().getLong("bans.trackWindowSeconds", 2592000L);
        String  reasonTpl   = getConfig().getString("bans.reasonTemplate", "Suspicious login attempt. Ban: %DURATION%.");

        // DB → migrations
        this.db = new DatabaseManager(this);
        this.db.start();

        // Crypto
        String keyB64 = getConfig().getString("security.tokenEncryptionKeyBase64", "");
        if (keyB64.isBlank()) {
            getLogger().severe("security.tokenEncryptionKeyBase64 is missing in config.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        TokenCrypto crypto = TokenCrypto.fromBase64(keyB64);

        // Repos
        this.accountsRepo    = new JdbcAccountsRepo(db.dataSource(), db.dialect());
        this.profileRepo     = new JdbcProfileRepo(db.dataSource(), db.dialect());
        this.tokensRepo      = new JdbcTokensRepo(db.dataSource(), crypto, db.dialect());
        this.discordUserRepo = new JdbcDiscordUserRepo(db.dataSource(), db.dialect());
        this.banProgressRepo = new JdbcBanProgressRepo(db.dataSource(), db.dialect());
        this.loginStatePort  = new LoginStateService();

        // OAuth
        String redirectUri = publicUrl + "/oauth/callback";
        this.oauthPort = new DiscordOAuthAdapter(getLogger(), clientId, clientSecret, scopes);

        // Core
        this.loginService = new LoginService(
                oauthPort, /* dmPort set after JDA */ null,
                accountsRepo, profileRepo, tokensRepo, loginStatePort, getLogger(),
                publicUrl, redirectUri,
                discordUserRepo,
                this,
                banProgressRepo,
                bansEnabled,
                baseSec, mult, maxSec, windowSec,
                reasonTpl,
                messages
        );


        // Discord
        this.jda = JDABuilder.createDefault(
                        botToken,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES
                )
                .addEventListeners(
                        new JdaSlashLoginListener(loginService, getLogger(), messages),
                        new JdaDiscordButtonListener(loginService, profileRepo, messages, getLogger()),
                        new ListenerAdapter() {
                            @Override public void onReady(ReadyEvent event) {
                                SlashCommandRegistrar.register(jda);
                                discordDmPort = new JdaDiscordDmAdapter(jda, getLogger(), messages, LogDO2.this);
                                loginService.setDiscordDmPort(discordDmPort);
                                getLogger().info("[LogDO2] JDA is READY. DM adapter installed.");
                            }
                        }
                ).build();

        this.discordDmPort = new JdaDiscordDmAdapter(jda, getLogger(), messages, this);
        this.loginService.setDiscordDmPort(discordDmPort);

        // Web
        this.loginEndpoint = new LoginEndpoint(getLogger(), loginService);
        this.loginEndpoint.start(webPort);

        LogDO2Command cmd = new LogDO2Command(loginService, accountsRepo, profileRepo, banProgressRepo, messages);
        Objects.requireNonNull(getCommand("logdo2")).setExecutor(cmd);
        Objects.requireNonNull(getCommand("logdo2")).setTabCompleter(cmd);

        // Bukkit listeners & timeouts
        this.floodgate = new FloodgateHook();
        if (floodgate.isPresent()) getLogger().info("[LogDO2] Floodgate detected. Bedrock support enabled.");
        Bukkit.getPluginManager().registerEvents(new PreLoginListener(banProgressRepo, getLogger(), messages), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(loginService, floodgate, loginStatePort, this), this);
        this.timeouts = new TimeoutManager(
                this, loginStatePort, loginService,
                Duration.ofSeconds(loginSec),
                Duration.ofSeconds(ipConfSec)
        );
        this.timeouts.start();

        getLogger().info("LogDO2 enabled with DB " + db.dialect());
    }

    @Override
    public void onDisable() {
        if (timeouts != null) timeouts.stop();
        if (loginEndpoint != null) loginEndpoint.stop();
        if (jda != null) jda.shutdownNow();
        if (db != null) db.stop();
        getLogger().info("LogDO2 disabled.");
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return null;
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
