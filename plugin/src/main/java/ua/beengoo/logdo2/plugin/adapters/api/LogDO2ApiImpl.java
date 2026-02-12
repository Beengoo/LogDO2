package ua.beengoo.logdo2.plugin.adapters.api;

import lombok.Setter;
import net.dv8tion.jda.api.JDA;
import ua.beengoo.logdo2.api.LogDO2Api;
import ua.beengoo.logdo2.api.entity.LogDO2Profile;
import ua.beengoo.logdo2.api.entity.LogDO2ProfileFactory;
import ua.beengoo.logdo2.api.entity.WebServerInfo;
import ua.beengoo.logdo2.api.spi.repo.AccountsRepo;
import ua.beengoo.logdo2.api.spi.repo.DiscordUserRepo;
import ua.beengoo.logdo2.api.spi.repo.ProfileRepo;
import ua.beengoo.logdo2.api.spi.repo.TokensRepo;
import ua.beengoo.logdo2.core.service.LoginService;
import ua.beengoo.logdo2.core.service.LoginStateService;
import ua.beengoo.logdo2.plugin.LogDO2;

import java.util.UUID;

public class LogDO2ApiImpl implements LogDO2Api {
    private final LoginService service;
    private final ProfileRepo profiles;
    private final AccountsRepo accounts;
    private final TokensRepo tokens;
    private final DiscordUserRepo discordUsers;
    private final LoginStateService loginState;
    @Setter
    private JDA discordBot;
    private final String targetGuildId;

    public LogDO2ApiImpl(LoginService service,
                         ProfileRepo profiles,
                         AccountsRepo accounts,
                         TokensRepo tokens,
                         DiscordUserRepo discordUsers,
                         LoginStateService loginState,
                         JDA discordBot,
                         String targetGuildId) {
        this.service = service;
        this.profiles = profiles;
        this.accounts = accounts;
        this.tokens = tokens;
        this.discordUsers = discordUsers;
        this.loginState = loginState;
        this.discordBot = discordBot;
        this.targetGuildId = targetGuildId;
    }

    @Override
    public boolean isProxySoftware() {
        return false;
    }

    @Override
    public LogDO2Profile getProfile(Long discordId) {
        return LogDO2ProfileFactory.fromDiscordId(discordId, accounts, profiles, tokens, discordUsers).orElse(null);
    }

    @Override
    public LogDO2Profile getProfile(UUID minecraftUUID) {
        return LogDO2ProfileFactory.fromMinecraftUuid(minecraftUUID, accounts, profiles, tokens, discordUsers).orElse(null);
    }

    @Override
    public boolean isActionAllowed(UUID uuid, String currentIp) {
        return service.isActionAllowed(uuid, currentIp);
    }

    @Override
    public Object getDiscordBot() {
        return discordBot;
    }

    @Override
    public String getDiscordAPiProvider() {
        return "JDA";
    }

    @Override
    public WebServerInfo getWebServerInfo() {
        return LogDO2.getInstance().getWebServerInfo();
    }

    @Override
    public String getTargetGuildId() {
        return targetGuildId;
    }
}
