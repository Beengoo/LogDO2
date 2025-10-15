package ua.beengoo.logdo2.plugin;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
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
import ua.beengoo.logdo2.api.ports.*;
import ua.beengoo.logdo2.core.service.LoginService;
import ua.beengoo.logdo2.core.service.LoginStateService;
import ua.beengoo.logdo2.plugin.adapters.discord.JdaDiscordDmAdapter;
import ua.beengoo.logdo2.plugin.adapters.jdbc.*;
import ua.beengoo.logdo2.plugin.adapters.oauth.DiscordOAuthAdapter;
import ua.beengoo.logdo2.plugin.command.LogDO2Command;
import ua.beengoo.logdo2.plugin.config.Config;
import ua.beengoo.logdo2.plugin.db.DatabaseManager;
import ua.beengoo.logdo2.plugin.discord.JdaDiscordButtonListener;
import ua.beengoo.logdo2.plugin.discord.JdaSlashLoginListener;
import ua.beengoo.logdo2.plugin.discord.SlashCommandRegistrar;
import ua.beengoo.logdo2.plugin.i18n.YamlMessages;
import ua.beengoo.logdo2.plugin.listeners.PlayerListener;
import ua.beengoo.logdo2.plugin.listeners.PreLoginListener;
import ua.beengoo.logdo2.plugin.integration.FloodgateHook;
import ua.beengoo.logdo2.plugin.props.LogDO2PropertiesManager;
import ua.beengoo.logdo2.plugin.runtime.TimeoutManager;
import ua.beengoo.logdo2.plugin.util.EnumsUtil;
import ua.beengoo.logdo2.plugin.util.StringUtil;
import ua.beengoo.logdo2.plugin.util.TokenCrypto;
import ua.beengoo.logdo2.plugin.web.LoginEndpoint;
import ua.beengoo.logdo2.plugin.util.AuditLogger;
import ua.beengoo.logdo2.plugin.adapters.api.AccountsReadAdapter;
import ua.beengoo.logdo2.plugin.adapters.api.LogDO2ApiImpl;
import ua.beengoo.logdo2.plugin.adapters.api.ProfileReadAdapter;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Slf4j
public final class LogDO2 extends JavaPlugin {
    private JDA jda;
    private LoginEndpoint loginEndpoint;
    private TimeoutManager timeouts;
    private AuditLogger audit;

    @Getter
    private LoginService loginService;

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
    private FloodgateHook floodgate;

    @Override
    public void onEnable() {
        Config.init(this);
        saveDefaultConfig();
        Config.updateConfigDefaults();
        this.messages = new YamlMessages(this);

        int    webPort      = getConfig().getInt("web.port", 8080);
        String publicUrl    = StringUtil.stripTrailingSlash(getConfig().getString("web.publicUrl", "http://localhost:" + webPort));
        String botToken     = getConfig().getString("discord.botToken", "");
        String clientId     = getConfig().getString("oauth.clientId", "");
        String clientSecret = getConfig().getString("oauth.clientSecret", "");
        String scopes       = getConfig().getString("oauth.scopes", "identify email applications.commands");

        List<String> intentNames = getConfig().getStringList("discord.intents");

        boolean enableCacheChunking = getConfig().getBoolean("discord.enableCacheChunking");
        boolean cacheAllGuildMembers = getConfig().getBoolean("discord.cacheAllGuildMembers");

        long loginSec  = getConfig().getLong("timeouts.loginSeconds", 300L);
        long ipConfSec = getConfig().getLong("timeouts.ipConfirmSeconds", 180L);

        int     bctal       = getConfig().getInt("platform.bedrockCodeTimeAfterLeave");

        // DB → migrations
        this.db = new DatabaseManager(this);
        this.db.start();

        // Crypto
        String keyB64 = getConfig().getString("security.tokenEncryptionKeyBase64", "");
        if (keyB64.isBlank()) {
            log.error("security.tokenEncryptionKeyBase64 is missing in config.yml");
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
        this.loginStatePort  = new LoginStateService(LogDO2PropertiesManager.getINSTANCE());

        // OAuth (allow external override via ServicesManager)
        String redirectUri = publicUrl + "/oauth/callback";
        this.oauthPort = getServer().getServicesManager().load(OAuthPort.class);
        if (this.oauthPort == null) {
            this.oauthPort = new DiscordOAuthAdapter(getLogger(), clientId, clientSecret, scopes);
        } else {
            log.info("Using external OAuthPort provider.");
        }

        // Core
        this.loginService = new LoginService(
                oauthPort, /* dmPort set after JDA */ null,
                accountsRepo, profileRepo, tokensRepo, loginStatePort, getLogger(),
                publicUrl, redirectUri,
                discordUserRepo,
                this,
                banProgressRepo,
                LogDO2PropertiesManager.getINSTANCE(),
                messages
        );



        // Discord
        var jdaBuilder = JDABuilder.createDefault(
                        botToken,
                        EnumsUtil.parseEnums(GatewayIntent.class, intentNames)
                )
                .addEventListeners(
                        new JdaSlashLoginListener(loginService, getLogger(), messages, audit),
                        new JdaDiscordButtonListener(loginService, profileRepo, messages, getLogger(), audit),
                        new ListenerAdapter() {
                            @Override public void onReady(@NotNull ReadyEvent event) {
                                SlashCommandRegistrar.register(jda);
                                // Install default DM adapter only if no external provider was set earlier
                                if (discordDmPort == null) {
                                    discordDmPort = new JdaDiscordDmAdapter(jda, getLogger(), messages, LogDO2.this);
                                    loginService.setDiscordDmPort(discordDmPort);
                                } else {
                                    log.info("External DM adapter used.");
                                }
                            }
                        }
                );

        if (enableCacheChunking) {
            jdaBuilder.setMemberCachePolicy(MemberCachePolicy.ALL);
        }
        if (cacheAllGuildMembers) {
            jdaBuilder.setChunkingFilter(ChunkingFilter.ALL);
        }

        this.jda = jdaBuilder.build();

        // Discord DM adapter (prefer external provider if present)
        DiscordDmPort externalDm = getServer().getServicesManager().load(DiscordDmPort.class);
        if (externalDm != null) {
            this.discordDmPort = externalDm;
            this.loginService.setDiscordDmPort(discordDmPort);
            log.info("Using external DM provider.");
        } else {
            this.discordDmPort = new JdaDiscordDmAdapter(jda, getLogger(), messages, this);
            this.loginService.setDiscordDmPort(discordDmPort);
        }

        // Audit
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

        // Web
        String postAction = getConfig().getString("postLogin.action", "text");
        String postText = getConfig().getString("postLogin.text", "Discord account linked. You can return to the game.");
        String redirectUrlCfg = getConfig().getString("postLogin.redirectUrl", "");
        String targetGuildId = getConfig().getString("discord.targetGuildId", "");
        String inviteChannelId = getConfig().getString("discord.inviteChannelId", "");

        this.loginEndpoint = new LoginEndpoint(
                getLogger(), loginService,
                jda,
                postAction, postText, redirectUrlCfg,
                targetGuildId, inviteChannelId,
                audit
        );
        this.loginEndpoint.start(webPort);

        LogDO2Command cmd = new LogDO2Command(this, loginService, accountsRepo, profileRepo, banProgressRepo, messages, audit);
        Objects.requireNonNull(getCommand("logdo2")).setExecutor(cmd);
        Objects.requireNonNull(getCommand("logdo2")).setTabCompleter(cmd);

        // Bukkit listeners & timeouts
        this.floodgate = new FloodgateHook();
        if (floodgate.isPresent()) log.info("Floodgate is supported!");
        Bukkit.getPluginManager().registerEvents(new PreLoginListener(banProgressRepo, getLogger(), messages, audit), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(loginService, floodgate, loginStatePort, this, audit), this);
        this.timeouts = new TimeoutManager(
                this, loginStatePort, loginService,
                Duration.ofSeconds(loginSec),
                Duration.ofSeconds(ipConfSec),
                Duration.ofSeconds(bctal)
        );
        this.timeouts.start();

        // Optional policy provider
        IpPolicyPort ipPolicy = getServer().getServicesManager().load(IpPolicyPort.class);
        if (ipPolicy != null) {
            loginService.setIpPolicyPort(ipPolicy);
            log.info("External IP Policy in use.");
        }

        // Register public services for integrations (read-only)
        var sm = getServer().getServicesManager();
        sm.register(ProfileReadPort.class, new ProfileReadAdapter(profileRepo), this, ServicePriority.Normal);
        sm.register(ua.beengoo.logdo2.api.ports.AccountsReadPort.class, new AccountsReadAdapter(accountsRepo), this, ServicePriority.Normal);
        sm.register(LogDO2Api.class, new LogDO2ApiImpl(loginService, profileRepo, accountsRepo, loginStatePort, jda, targetGuildId), this, ServicePriority.Normal);
        // Expose LoginStatePort for admin commands or integrations that need temporary flags
        sm.register(LoginStatePort.class, loginStatePort, this, ServicePriority.Normal);

        log.info("Using SQL dialect: {}", db.dialect());
        log.info("LogDO2 is ready!");
    }

    @Override
    public void onDisable() {
        if (timeouts != null) timeouts.stop();
        if (loginEndpoint != null) loginEndpoint.stop();
        if (jda != null) jda.shutdownNow();
        if (db != null) db.stop();
        if (audit != null) try { audit.close(); } catch (Exception ignored) {}
        log.info("LogDO2 disabled, bye-bye!");
    }
}
