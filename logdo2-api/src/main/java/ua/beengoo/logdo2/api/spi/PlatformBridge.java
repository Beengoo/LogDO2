package ua.beengoo.logdo2.api.spi;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Platform abstraction layer for plugins implementation.
 * This interface allows the core module to perform platform-specific operations
 * without depending directly on Bukkit APIs.
 */
public interface PlatformBridge {

    /**
     * Get a player's name by their UUID
     * @param uuid The player's UUID
     * @return The player's name, or empty if not found
     */
    Optional<String> getPlayerNameByUuid(UUID uuid);

    /**
     * Check if a player is currently online
     * @param uuid The player's UUID
     * @return true if the player is online, false otherwise
     */
    boolean isPlayerOnline(UUID uuid);

    /**
     * Execute a task on the main server thread
     * @param task The task to execute
     */
    void executeOnMainThread(Runnable task);

    /**
     * Execute a task on a player's thread (for Folia compatibility)
     * If the platform doesn't support per-player threads, executes on main thread
     * @param playerUuid The player's UUID
     * @param playerAction The action to execute, receives the player object
     */
    void executeOnPlayerThread(UUID playerUuid, Consumer<Object> playerAction);

    /**
     * Check if currently executing on the main server thread
     * @return true if on main thread, false otherwise
     */
    boolean isPrimaryThread();
}
