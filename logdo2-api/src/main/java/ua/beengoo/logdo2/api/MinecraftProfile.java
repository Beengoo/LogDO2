package ua.beengoo.logdo2.api;

import java.util.Optional;
import java.util.UUID;

public record MinecraftProfile(
        UUID uuid,
        String name,
        MinecraftPlatform platform,
        String lastConfirmedIp,
        Optional<Long> linkedDiscord
) {
    public boolean isLinked() { return linkedDiscord != null && linkedDiscord.isPresent(); }
    public Optional<Long> linkedDiscordId() { return linkedDiscord; }

    public enum MinecraftPlatform { JAVA, BEDROCK, UNKNOWN;
        public static MinecraftPlatform fromDatabase(String v) {
            if (v == null || v.isBlank()) return UNKNOWN;
            return switch (v.toUpperCase()) {
                case "JAVA" -> JAVA;
                case "BEDROCK" -> BEDROCK;
                default -> UNKNOWN;
            };
        }
    }
}
