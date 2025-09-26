package ua.beengoo.logdo2.api;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregated view of everything LogDO2 knows about a Minecraft profile.
 */
public record LogDO2User(
        MinecraftProfile profile,
        DiscordLink discord,
        Session session
) {

    public LogDO2User {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(discord, "discord");
        Objects.requireNonNull(session, "session");
    }

    public record MinecraftProfile(
            UUID uuid,
            String name,
            MinecraftPlatform platform,
            String lastConfirmedIp
    ) {
        public MinecraftProfile {
            Objects.requireNonNull(uuid, "uuid");
            platform = platform == null ? MinecraftPlatform.UNKNOWN : platform;
        }
    }

    public enum MinecraftPlatform {
        JAVA,
        BEDROCK,
        UNKNOWN;

        public static MinecraftPlatform fromDatabase(String value) {
            if (value == null || value.isBlank()) return UNKNOWN;
            return switch (value.toUpperCase(Locale.ROOT)) {
                case "JAVA" -> JAVA;
                case "BEDROCK" -> BEDROCK;
                default -> UNKNOWN;
            };
        }
    }

    public record DiscordLink(
            Optional<Long> activeDiscordId,
            Optional<Long> anyDiscordId,
            Set<LinkedProfile> profiles
    ) {
        public DiscordLink {
            Objects.requireNonNull(activeDiscordId, "activeDiscordId");
            Objects.requireNonNull(anyDiscordId, "anyDiscordId");
            LinkedHashSet<LinkedProfile> ordered = new LinkedHashSet<>();
            if (profiles != null) ordered.addAll(profiles);
            profiles = Collections.unmodifiableSet(ordered);
        }

        public boolean isLinked() {
            return activeDiscordId.isPresent();
        }
    }

    public record LinkedProfile(
            MinecraftProfile profile,
            boolean primary
    ) {
        public LinkedProfile {
            Objects.requireNonNull(profile, "profile");
        }
    }

    public record Session(
            Optional<PendingLogin> pendingLogin,
            Optional<PendingIpConfirm> pendingIpConfirm,
            boolean limitBypassGranted
    ) {
        public Session {
            Objects.requireNonNull(pendingLogin, "pendingLogin");
            Objects.requireNonNull(pendingIpConfirm, "pendingIpConfirm");
        }

        public boolean isPendingLogin() {
            return pendingLogin.isPresent();
        }

        public boolean isPendingIpConfirm() {
            return pendingIpConfirm.isPresent();
        }
    }

    public record PendingLogin(
            String ip,
            boolean bedrock,
            Instant since
    ) {
        public PendingLogin {
            Objects.requireNonNull(since, "since");
        }
    }

    public record PendingIpConfirm(
            String newIp,
            long discordId,
            Instant since
    ) {
        public PendingIpConfirm {
            Objects.requireNonNull(since, "since");
        }
    }
}
