package ua.beengoo.logdo2.api.entity;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class for generating and decoding profile IDs.
 * Profile IDs are Base64-encoded strings that uniquely identify a LogDO2 profile.
 */
public class ProfileIdGenerator {

    /**
     * Generate a unique profile ID from Discord ID and creation timestamp.
     * The format is Base64(discordId + "_" + createdAt).
     *
     * @param discordId Discord user ID
     * @param createdAt Unix timestamp (seconds) when account was created
     * @return Base64-encoded profile ID (URL-safe, without padding)
     */
    public static String generate(long discordId, long createdAt) {
        String raw = discordId + "_" + createdAt;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decode a profile ID back to its components.
     * This is primarily for debugging and validation purposes.
     *
     * @param profileId Base64-encoded profile ID
     * @return Array containing [discordId, createdAt], or null if invalid
     */
    public static long[] decode(String profileId) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(profileId);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = raw.split("_");
            if (parts.length != 2) return null;
            return new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])};
        } catch (Exception e) {
            return null;
        }
    }
}