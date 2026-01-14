package ua.beengoo.logdo2.api.spi.providers;

import java.util.UUID;

/**
 * Provider for checking if a player is connecting from Bedrock Edition via Floodgate.
 */
public interface FloodgateProvider {
    /**
     * Checks if Floodgate is available and properly initialized.
     *
     * @return true if Floodgate is present and functional
     */
    boolean isAvailable();

    /**
     * Checks if the given player UUID belongs to a Bedrock Edition player.
     *
     * @param uuid the player's UUID
     * @return true if the player is connecting from Bedrock Edition
     */
    boolean isBedrockPlayer(UUID uuid);
}
