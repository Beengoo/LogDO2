package ua.beengoo.logdo2.api.spi.providers;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Provider interface for registering custom login conditions.
 * External plugins can implement this interface and register it via Bukkit's ServiceManager
 * to add custom checks that must pass before players can join the server.
 *
 * <p>Example registration:</p>
 * <pre>
 * getServer().getServicesManager().register(
 *     LoginConditionProvider.class,
 *     myProvider,
 *     myPlugin,
 *     ServicePriority.Normal
 * );
 * </pre>
 */
public interface LoginConditionProvider {

    /**
     * Priority constants for condition ordering.
     * Higher priority conditions are evaluated first.
     */
    int PRIORITY_HIGHEST = 200;
    int PRIORITY_HIGH = 100;
    int PRIORITY_NORMAL = 0;
    int PRIORITY_LOW = -100;
    int PRIORITY_LOWEST = -200;

    /**
     * Returns all login conditions provided by this provider.
     * Called once during initialization and after reloads.
     *
     * @return Collection of login conditions to evaluate
     */
    Collection<LoginCondition> getConditions();

    /**
     * Represents a single login condition that must be evaluated.
     */
    interface LoginCondition {

        /**
         * Unique identifier for this condition (used for debugging/logging).
         * Examples: "logdo2:simultaneous_play", "myplugin:geo_restriction"
         *
         * @return Unique condition ID
         */
        String getId();

        /**
         * Priority for evaluation order. Higher values evaluated first.
         * Use the PRIORITY_* constants for standard priorities.
         *
         * @return Priority value
         */
        default int getPriority() {
            return PRIORITY_NORMAL;
        }

        /**
         * Evaluates whether the player should be allowed to join.
         * Called during PlayerLoginEvent (async context).
         *
         * @param context Context information about the login attempt
         * @return Result indicating success or failure with optional message
         */
        Result evaluate(LoginContext context);
    }

    /**
     * Context information provided to login conditions during evaluation.
     */
    interface LoginContext {
        /** Player's unique ID */
        UUID getUuid();

        /** Player's current name */
        String getName();

        /** Player's IP address */
        String getIp();

        /** Whether player is connecting from Bedrock edition */
        boolean isBedrock();

        /**
         * Access to platform-independent services.
         * External plugins should avoid accessing Bukkit directly in conditions.
         */
        PlatformContext getPlatform();
    }

    /**
     * Platform-specific context.
     */
    interface PlatformContext {
        /**
         * Check if another player with the same Discord account is online.
         * Built-in helper to avoid external plugins needing repo access.
         *
         * @param uuid The player's UUID to check
         * @return UUID of other online player, or empty if none
         */
        Optional<UUID> getOtherOnlineProfileWithSameAccount(UUID uuid);
    }

    /**
     * Result of a condition evaluation.
     *
     * @param success Whether the condition passed
     * @param message Optional message (MiniMessage format).
     *                Required if success=false, optional if success=true
     */
    record Result(boolean success, String message) {

        /** Shorthand for successful evaluation */
        public static Result allow() {
            return new Result(true, null);
        }

        /** Shorthand for failed evaluation with message */
        public static Result deny(String message) {
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("Deny message cannot be null or blank");
            }
            return new Result(false, message);
        }

        /** Validation: deny must have a message */
        public Result {
            if (!success && (message == null || message.isBlank())) {
                throw new IllegalArgumentException("Failed condition must provide a message");
            }
        }
    }
}
