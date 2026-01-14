package ua.beengoo.logdo2.plugin.conditions.login;

import lombok.extern.slf4j.Slf4j;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import ua.beengoo.logdo2.api.spi.providers.LoginConditionProvider;
import ua.beengoo.logdo2.api.spi.providers.LoginConditionProvider.LoginCondition;
import ua.beengoo.logdo2.api.spi.providers.LoginConditionProvider.LoginContext;
import ua.beengoo.logdo2.api.spi.providers.LoginConditionProvider.Result;
import ua.beengoo.logdo2.api.spi.providers.MessagesProvider;
import ua.beengoo.logdo2.api.spi.repo.AccountsRepo;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Orchestrates evaluation of all registered login conditions.
 * Collects providers from ServiceManager, sorts by priority, and evaluates.
 */
@Slf4j(topic = "LogDO2")
public class LoginConditionEvaluator {

    private final Plugin plugin;
    private final AccountsRepo accountsRepo;
    private final MessagesProvider messages;
    
    private volatile List<LoginCondition> sortedConditions;

    public LoginConditionEvaluator(Plugin plugin, AccountsRepo accountsRepo, MessagesProvider messages) {
        this.plugin = plugin;
        this.accountsRepo = accountsRepo;
        this.messages = messages;
    }

    /**
     * Rebuilds the cached list of conditions from all registered providers.
     * Call this after plugin reload or when conditions may have changed.
     */
    public void rebuildConditionCache() {
        List<LoginCondition> conditions = new ArrayList<>();
        
        Collection<RegisteredServiceProvider<LoginConditionProvider>> providers =
            plugin.getServer().getServicesManager().getRegistrations(LoginConditionProvider.class);

        for (RegisteredServiceProvider<LoginConditionProvider> registration : providers) {
            try {
                LoginConditionProvider provider = registration.getProvider();
                Collection<LoginCondition> providerConditions = provider.getConditions();
                if (providerConditions != null) {
                    conditions.addAll(providerConditions);
                }
            } catch (Exception e) {
                log.warn("Failed to load conditions from provider: {}", e.getMessage());
            }
        }
        
        conditions.sort(Comparator.comparingInt(LoginCondition::getPriority).reversed());

        this.sortedConditions = Collections.unmodifiableList(conditions);
    }

    /**
     * Evaluates all conditions for a login attempt.
     *
     * @param uuid Player UUID
     * @param name Player name
     * @param ip Player IP
     * @param bedrock Whether player is Bedrock edition
     * @return Optional containing combined failure message, or empty if all conditions pass
     */
    public Optional<String> evaluateConditions(UUID uuid, String name, String ip, boolean bedrock) {
        LoginContext context = new LoginContextImpl(uuid, name, ip, bedrock, accountsRepo);
        List<String> failures = new ArrayList<>();

        for (LoginCondition condition : sortedConditions) {
            try {
                Result result = condition.evaluate(context);
                if (!result.success()) {
                    failures.add(result.message());
                }
            } catch (Exception e) {
                log.warn("Condition {} threw exception", condition.getId(), e);
            }
        }

        if (failures.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(combineFailureMessages(failures));
    }

    /**
     * Combines multiple failure messages into a single formatted message.
     */
    private String combineFailureMessages(List<String> failures) {
        if (failures.size() == 1) {
            Map<String, String> placeholders = Map.of("reason", failures.get(0));
            return messages.mc("login.conditions.denied_single", placeholders);
        }
        
        String bulletList = failures.stream()
            .map(msg -> "<gray>•</gray> " + msg)
            .collect(Collectors.joining("\n"));

        Map<String, String> placeholders = Map.of("reasons", bulletList);
        return messages.mc("login.conditions.denied_multi", placeholders);
    }

    /**
     * Implementation of LoginContext.
     * */
    private static class LoginContextImpl implements LoginContext {
        private final UUID uuid;
        private final String name;
        private final String ip;
        private final boolean bedrock;
        private final LoginConditionProvider.PlatformContext platform;

        LoginContextImpl(UUID uuid, String name, String ip, boolean bedrock, AccountsRepo accountsRepo) {
            this.uuid = uuid;
            this.name = name;
            this.ip = ip;
            this.bedrock = bedrock;
            this.platform = new PlatformContextImpl(accountsRepo);
        }

        @Override public UUID getUuid() { return uuid; }
        @Override public String getName() { return name; }
        @Override public String getIp() { return ip; }
        @Override public boolean isBedrock() { return bedrock; }
        @Override public LoginConditionProvider.PlatformContext getPlatform() { return platform; }
    }

    /**
     * Implementation of PlatformContext.
     */
    private static class PlatformContextImpl implements LoginConditionProvider.PlatformContext {
        private final AccountsRepo accountsRepo;

        PlatformContextImpl(AccountsRepo accountsRepo) {
            this.accountsRepo = accountsRepo;
        }

        @Override
        public Optional<UUID> getOtherOnlineProfileWithSameAccount(UUID uuid) {
            var owner = accountsRepo.findDiscordForProfile(uuid);
            if (owner.isEmpty()) {
                return Optional.empty();
            }

            long discordId = owner.get();

            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.getUniqueId().equals(uuid)) continue;

                var otherOwner = accountsRepo.findDiscordForProfile(other.getUniqueId());
                if (otherOwner.isPresent() && otherOwner.get() == discordId) {
                    return Optional.of(other.getUniqueId());
                }
            }

            return Optional.empty();
        }
    }
}
