package ua.beengoo.logdo2.plugin;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import ua.beengoo.logdo2.api.LogDO2Api;
import ua.beengoo.logdo2.api.entity.WebServerInfo;
import ua.beengoo.logdo2.api.spi.PlatformBridge;
import ua.beengoo.logdo2.api.spi.callbacks.LoginCallbacks;
import ua.beengoo.logdo2.api.spi.repo.*;
import ua.beengoo.logdo2.api.spi.providers.*;
import ua.beengoo.logdo2.core.service.LoginService;
import ua.beengoo.logdo2.core.service.LoginStateService;
import ua.beengoo.logdo2.plugin.adapters.discord.JdaDiscordDmAdapter;
import ua.beengoo.logdo2.plugin.adapters.jdbc.*;
import ua.beengoo.logdo2.plugin.adapters.oauth.DiscordOAuthAdapter;
import ua.beengoo.logdo2.plugin.adapters.platform.BukkitLoginCallbacks;
import ua.beengoo.logdo2.plugin.adapters.platform.BukkitPlatformBridge;
import ua.beengoo.logdo2.plugin.command.LogDO2Command;
import ua.beengoo.logdo2.plugin.config.Config;
import ua.beengoo.logdo2.plugin.db.DatabaseManager;
import ua.beengoo.logdo2.plugin.discord.JDAButtonInteractionListener;
import ua.beengoo.logdo2.plugin.discord.JDALoginCommandListener;
import ua.beengoo.logdo2.plugin.discord.JDAPrimaryCommandListener;
import ua.beengoo.logdo2.plugin.discord.SlashCommandRegistrar;
import ua.beengoo.logdo2.plugin.i18n.YamlMessages;
import ua.beengoo.logdo2.plugin.listeners.LogDO2Listener;
import ua.beengoo.logdo2.plugin.listeners.game.PlayerListener;
import ua.beengoo.logdo2.api.spi.providers.FloodgateProvider;
import ua.beengoo.logdo2.plugin.adapters.floodgate.FloodgateAdapter;
import ua.beengoo.logdo2.plugin.listeners.ReloadListener;
import ua.beengoo.logdo2.plugin.props.LogDO2PropertiesManager;
import ua.beengoo.logdo2.plugin.runtime.TimeoutManager;
import ua.beengoo.logdo2.plugin.util.EncryptionManager;
import ua.beengoo.logdo2.plugin.util.EnumsUtil;
import ua.beengoo.logdo2.plugin.util.StringUtil;
import ua.beengoo.logdo2.plugin.web.HttpLoginServer;
import ua.beengoo.logdo2.plugin.util.AuditLogger;
import ua.beengoo.logdo2.plugin.adapters.api.LogDO2ApiImpl;
import ua.beengoo.logdo2.plugin.conditions.login.LoginConditionEvaluator;
import ua.beengoo.logdo2.plugin.conditions.login.LogDO2LoginConditionsProvider;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Random;

@Slf4j(topic = "LogDO2")
public final class LogDO2 extends JavaPlugin {

    @Getter
    private static LogDO2 instance;

    @Getter
    private JDA jda;
    @Getter
    private HttpLoginServer httpLoginServer;
    private TimeoutManager timeouts;
    private AuditLogger audit;

    @Getter
    private LoginService loginService;
    @Getter
    private OAuthProvider authProvider;
    @Getter
    private DiscordMessagesProvider discordMessageProvider;
    @Getter
    private AccountsRepo accountsRepo;
    @Getter
    private ProfileRepo profileRepo;
    @Getter
    private TokensRepo tokensRepo;
    @Getter
    private YamlMessages messages;
    @Getter
    private DiscordUserRepo discordUserRepo;
    @Getter
    private BanProgressRepo banProgressRepo;
    @Getter
    private LoginStateService loginStatePort;

    @Getter
    private FloodgateProvider floodgateProvider;

    private DatabaseManager db;
    @Getter
    private LogDO2ApiImpl logdo2API;
    @Getter
    private LoginConditionEvaluator conditionEvaluator;
    @Setter
    @Getter
    private WebServerInfo webServerInfo;

    @Override
    public void onEnable() {
        printBanner();
        instance = this;
        configureLogging();
        Config.init(this);
        saveDefaultConfig();
        Config.updateConfigDefaults();
        this.messages = new YamlMessages(this);

        this.webServerInfo = Config.buildWebServerInfo();

        String botToken     = getConfig().getString("discord.botToken", "");
        String clientId     = getConfig().getString("oauth.clientId", "");
        String clientSecret = getConfig().getString("oauth.clientSecret", "");
        String scopes       = getConfig().getString("oauth.scopes", "identify email applications.commands");

        List<String> intentNames = getConfig().getStringList("discord.intents");
        List<String> cacheFlags = getConfig().getStringList("discord.cacheFlags");

        boolean enableCacheChunking = getConfig().getBoolean("discord.enableCacheChunking");
        boolean cacheAllGuildMembers = getConfig().getBoolean("discord.cacheAllGuildMembers");

        long loginSec  = getConfig().getLong("timeouts.loginSeconds", 300L);
        long ipConfSec = getConfig().getLong("timeouts.ipConfirmSeconds", 180L);

        int     bctal       = getConfig().getInt("platform.bedrockCodeTimeAfterLeave");

        this.db = new DatabaseManager(this);
        this.db.start();

        String keyB64 = getConfig().getString("security.tokenEncryptionKeyBase64", "");
        if (keyB64.isBlank()) {
            log.error("security.tokenEncryptionKeyBase64 is missing! Please generate and set one in plugins/LogDO2/config.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        EncryptionManager crypto = EncryptionManager.fromBase64(keyB64);

        this.accountsRepo    = new JdbcAccountsRepo(db.dataSource(), db.dialect());
        this.profileRepo     = new JdbcProfileRepo(db.dataSource(), db.dialect());
        this.tokensRepo      = new JdbcTokensRepo(db.dataSource(), crypto, db.dialect());
        this.discordUserRepo = new JdbcDiscordUserRepo(db.dataSource(), db.dialect());
        this.banProgressRepo = new JdbcBanProgressRepo(db.dataSource(), db.dialect());
        this.loginStatePort  = new LoginStateService(LogDO2PropertiesManager.getINSTANCE());

        this.conditionEvaluator = new LoginConditionEvaluator(
            this, accountsRepo, messages
        );

        LogDO2LoginConditionsProvider builtinConditions = new LogDO2LoginConditionsProvider(
            banProgressRepo, accountsRepo,
            LogDO2PropertiesManager.getINSTANCE(), messages
        );
        getServer().getServicesManager().register(
            LoginConditionProvider.class,
            builtinConditions,
            this,
            ServicePriority.Normal
        );
        conditionEvaluator.rebuildConditionCache();
        this.authProvider = getServer().getServicesManager().load(OAuthProvider.class);
        if (this.authProvider == null) {
            this.authProvider = new DiscordOAuthAdapter(clientId, clientSecret, scopes);
        } else {
            log.info("Using external authentication provider");
        }

        PlatformBridge platformBridge = new BukkitPlatformBridge(this);
        LoginCallbacks loginCallbacks = new BukkitLoginCallbacks(this);

        this.loginService = new LoginService(
                authProvider, null,
                accountsRepo, profileRepo, tokensRepo, loginStatePort,
                webServerInfo,
                discordUserRepo,
                banProgressRepo,
                LogDO2PropertiesManager.getINSTANCE(),
                messages,
                platformBridge,
                loginCallbacks
        );

        startJDA(botToken, intentNames, enableCacheChunking, cacheAllGuildMembers, cacheFlags);

        DiscordMessagesProvider externalMP = getServer().getServicesManager().load(DiscordMessagesProvider.class);
        if (externalMP != null) {
            this.discordMessageProvider = externalMP;
            this.loginService.setDiscordDmPort(discordMessageProvider);
            log.info("Using external message provider");
        } else {
            this.discordMessageProvider = new JdaDiscordDmAdapter(jda, messages);
            this.loginService.setDiscordDmPort(discordMessageProvider);
        }

        boolean auditEnabled = getConfig().getBoolean("audit.enabled", true);
        String auditFile = getConfig().getString("audit.file", "logdo2-actions.log");
        if (auditEnabled) {
            try {
                this.audit = new AuditLogger(this, auditFile);
            } catch (Exception e) {
                log.warn("Failed to open audit log", e);
            }
        } else {
            this.audit = null;
        }

        String postAction = getConfig().getString("postLogin.action", "text");
        String postText = getConfig().getString("postLogin.text", "Discord account linked. You can return to the game.");
        String redirectUrlCfg = getConfig().getString("postLogin.redirectUrl", "");
        String targetGuildId = getConfig().getString("discord.targetGuildId", "");
        String inviteChannelId = getConfig().getString("discord.inviteChannelId", "");

        this.httpLoginServer = new HttpLoginServer(
                loginService,
                jda,
                postAction, postText, redirectUrlCfg,
                targetGuildId, inviteChannelId,
                audit
        );
        this.httpLoginServer.start();

        this.logdo2API = new LogDO2ApiImpl(loginService, profileRepo, accountsRepo, tokensRepo, discordUserRepo, loginStatePort, jda, targetGuildId);

        LogDO2Command cmd = new LogDO2Command(logdo2API, accountsRepo, profileRepo, banProgressRepo, discordUserRepo, messages, audit, jda);
        Objects.requireNonNull(getCommand("logdo2")).setExecutor(cmd);
        Objects.requireNonNull(getCommand("logdo2")).setTabCompleter(cmd);

        this.floodgateProvider = new FloodgateAdapter();
        if (this.floodgateProvider.isAvailable()) log.info("Found Floodgate!");
        Bukkit.getPluginManager().registerEvents(new LogDO2Listener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(loginService, this.floodgateProvider, loginStatePort, this, audit, conditionEvaluator), this);
        Bukkit.getPluginManager().registerEvents(new ReloadListener(this), this);
        this.timeouts = new TimeoutManager(
                this, loginStatePort, loginService,
                Duration.ofSeconds(loginSec),
                Duration.ofSeconds(ipConfSec),
                Duration.ofSeconds(bctal)
        );
        this.timeouts.start();

        var sm = getServer().getServicesManager();
        sm.register(LogDO2Api.class, this.logdo2API, this, ServicePriority.Normal);
        sm.register(LoginStateService.class, loginStatePort, this, ServicePriority.Normal);

        log.info("Database in use: {}", db.dialect());
        log.info("LogDO2 is ready!");
    }

    private void printBanner(){
        String[] splashes = new String[]{
                "Where do i type password?",
                "Hytale soon",
                "By Beengoo",
                "Not that simple as it sounds",
                "Your IP is: localhost",
        };
        String art = """
                    __                ____  ____ ___\s
                   / /   ____  ____ _/ __ \\/ __ \\__ \\
                  / /   / __ \\/ __ `/ / / / / / /_/ /
                 / /___/ /_/ / /_/ / /_/ / /_/ / __/\s
                /_____/\\____/\\__, /_____/\\____/____/\s
                            /____/                  \s
                > %s
                """.formatted(splashes[new Random().nextInt(0, splashes.length-1)]);

        for (String line:art.split("\n")) {log.info(line);}
    }

    public void startJDA(String botToken, List<String> intentNames,
                        boolean enableCacheChunking, boolean cacheAllGuildMembers,
                         List<String> cacheFlags){
        var jdaBuilder = JDABuilder.createDefault(
                        botToken,
                        EnumsUtil.parseEnums(GatewayIntent.class, intentNames)
                )
                .addEventListeners(
                        new JDALoginCommandListener(loginService, messages, audit),
                        new JDAButtonInteractionListener(loginService, profileRepo, messages, audit),
                        new JDAPrimaryCommandListener(accountsRepo, profileRepo, messages, audit),
                        new ListenerAdapter() {
                            @Override public void onReady(@NotNull ReadyEvent event) {
                                SlashCommandRegistrar.register(jda);
                                if (discordMessageProvider == null) {
                                    discordMessageProvider = new JdaDiscordDmAdapter(jda, messages);
                                    loginService.setDiscordDmPort(discordMessageProvider);
                                }
                            }
                        }
                ).enableCache(EnumsUtil.parseEnums(CacheFlag.class, cacheFlags));

        if (enableCacheChunking) {
            jdaBuilder.setMemberCachePolicy(MemberCachePolicy.ALL);
        }
        if (cacheAllGuildMembers) {
            jdaBuilder.setChunkingFilter(ChunkingFilter.ALL);
        }
        this.jda = jdaBuilder.build();
    }

    private void shutdownJDA(){
        if (jda != null) {
            try {
                log.info("Waiting 2 seconds for JDA to shutdown properly...");
                jda.awaitShutdown(Duration.ofSeconds(2));
            } catch (Exception e) {
                log.warn("JDA shutdown was interrupted or timed out!");
            }
        }
    }

    public void restartJDA(String botToken, List<String> intentNames,
                            boolean enableCacheChunking, boolean cacheAllGuildMembers, List<String> cacheFlags){
        shutdownJDA();
        if (jda != null) {
            jda = null;
        }
        startJDA(botToken, intentNames, enableCacheChunking, cacheAllGuildMembers, cacheFlags);
    }

    private void configureLogging() {
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            Configuration config = context.getConfiguration();

            // Mute JDA info logs
            LoggerConfig jdaLogger = config.getLoggerConfig("net.dv8tion.jda");
            if (jdaLogger.getLevel().isMoreSpecificThan(Level.WARN)) {
                jdaLogger.setLevel(Level.WARN);
            } else {
                config.addLogger("net.dv8tion.jda", new LoggerConfig("net.dv8tion.jda", Level.WARN, true));
            }

            // Mute Javalin info logs
            LoggerConfig javalinLogger = config.getLoggerConfig("io.javalin");
            if (javalinLogger.getLevel().isMoreSpecificThan(Level.WARN)) {
                javalinLogger.setLevel(Level.WARN);
            } else {
                config.addLogger("io.javalin", new LoggerConfig("io.javalin", Level.WARN, true));
            }

            // Mute Jetty info logs (used by Javalin)
            LoggerConfig jettyLogger = config.getLoggerConfig("org.eclipse.jetty");
            if (jettyLogger.getLevel().isMoreSpecificThan(Level.WARN)) {
                jettyLogger.setLevel(Level.WARN);
            } else {
                config.addLogger("org.eclipse.jetty", new LoggerConfig("org.eclipse.jetty", Level.WARN, true));
            }

            // Mute Hikari info logs
            LoggerConfig hikariLogger = config.getLoggerConfig("com.zaxxer.hikari");
            if (hikariLogger.getLevel().isMoreSpecificThan(Level.WARN)) {
                hikariLogger.setLevel(Level.WARN);
            } else {
                config.addLogger("com.zaxxer.hikari", new LoggerConfig("com.zaxxer.hikari", Level.WARN, true));
            }

            context.updateLoggers();
            log.debug("Logging configuration applied: JDA, Javalin, and Jetty set to WARN level");
        } catch (Exception e) {
            log.warn("Failed to configure logging levels: {}", e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (timeouts != null) timeouts.stop();
        if (httpLoginServer != null) httpLoginServer.stop();
        if (db != null) db.stop();
        shutdownJDA();
        if (audit != null) try { audit.close(); } catch (Exception ignored) {}
        log.info("LogDO2 disabled, bye-bye!");
    }
}
