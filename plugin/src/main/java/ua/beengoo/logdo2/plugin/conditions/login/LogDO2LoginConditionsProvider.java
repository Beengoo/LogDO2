package ua.beengoo.logdo2.plugin.conditions.login;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ua.beengoo.logdo2.api.spi.providers.LoginConditionProvider;
import ua.beengoo.logdo2.api.spi.repo.AccountsRepo;
import ua.beengoo.logdo2.api.spi.repo.BanProgressRepo;
import ua.beengoo.logdo2.api.spi.providers.MessagesProvider;
import ua.beengoo.logdo2.api.spi.providers.PropertiesProvider;

import java.util.*;

/**
 * Provides built-in login conditions for LogDO2.
 * Registered via ServiceManager during plugin initialization.
 */
public class LogDO2LoginConditionsProvider implements LoginConditionProvider {

    private final List<LoginCondition> conditions;

    public LogDO2LoginConditionsProvider(
            BanProgressRepo banProgressRepo,
            AccountsRepo accountsRepo,
            PropertiesProvider propertiesProvider,
            MessagesProvider messages) {

        this.conditions = List.of(
            new IpBanCondition(banProgressRepo, messages),
            new SimultaneousPlayCondition(accountsRepo, propertiesProvider, messages)
        );
    }

    @Override
    public Collection<LoginCondition> getConditions() {
        return conditions;
    }

    /**
     * IP ban check condition.
     * Migrated from PreLoginListener.
     * High priority to fail fast on banned IPs.
     */
    private static class IpBanCondition implements LoginCondition {
        private final BanProgressRepo bans;
        private final MessagesProvider msg;

        IpBanCondition(BanProgressRepo bans, MessagesProvider msg) {
            this.bans = bans;
            this.msg = msg;
        }

        @Override
        public String getId() {
            return "logdo2:ip_ban";
        }

        @Override
        public int getPriority() {
            return PRIORITY_HIGH; // Check bans early
        }

        @Override
        public Result evaluate(LoginContext context) {
            String ip = context.getIp();
            if (ip == null || ip.isBlank()) {
                return Result.allow();
            }

            long now = System.currentTimeMillis() / 1000;
            var rec = bans.findByIp(ip);
            if (rec.isEmpty()) {
                return Result.allow();
            }

            long until = rec.get().lastBanUntilEpochSec();
            if (until <= now) {
                return Result.allow();
            }

            long remain = until - now;
            Map<String, String> placeholders = Map.of("remaining", humanDuration(remain));
            String message = msg.mc("prelogin.banned", placeholders);
            return Result.deny(message);
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

    /**
     * Simultaneous play prevention condition.
     * Migrated from PlayerListener.disallowReasonOnLogin().
     */
    private static class SimultaneousPlayCondition implements LoginCondition {
        private final AccountsRepo accounts;
        private final PropertiesProvider props;
        private final MessagesProvider msg;

        SimultaneousPlayCondition(AccountsRepo accounts, PropertiesProvider props, MessagesProvider msg) {
            this.accounts = accounts;
            this.props = props;
            this.msg = msg;
        }

        @Override
        public String getId() {
            return "logdo2:simultaneous_play";
        }

        @Override
        public int getPriority() {
            return PRIORITY_NORMAL;
        }

        @Override
        public Result evaluate(LoginContext context) {
            var properties = props.getSnapshot();
            if (properties == null || !properties.disallowSimultaneousPlay) {
                return Result.allow();
            }

            var owner = accounts.findDiscordForProfile(context.getUuid());
            if (owner.isEmpty()) {
                return Result.allow();
            }

            long discordId = owner.get();

            // Check if any other online player is linked to the same Discord account
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.getUniqueId().equals(context.getUuid())) continue;

                var otherOwner = accounts.findDiscordForProfile(other.getUniqueId());
                if (otherOwner.isPresent() && otherOwner.get() == discordId) {
                    Map<String, String> placeholders = Map.of("other", other.getName());
                    String message = msg.mc("limits.simultaneous_kick", placeholders);
                    return Result.deny(message);
                }
            }

            return Result.allow();
        }
    }
}
