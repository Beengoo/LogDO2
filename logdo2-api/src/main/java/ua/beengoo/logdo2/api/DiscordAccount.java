package ua.beengoo.logdo2.api;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public record DiscordAccount(
        long discordId,
        List<MinecraftProfileSummary> profiles
) {
    public List<MinecraftProfileSummary> profiles() { return Collections.unmodifiableList(profiles); }

    public boolean hasProfile(java.util.UUID uuid) {
        return profiles.stream().anyMatch(p -> p.uuid().equals(uuid));
    }

    public Optional<MinecraftProfileSummary> findByUuid(java.util.UUID uuid) {
        return profiles.stream().filter(p -> p.uuid().equals(uuid)).findFirst();
    }

    public Optional<MinecraftProfileSummary> primary() {
        return profiles.isEmpty() ? Optional.empty() : Optional.of(profiles.get(0));
    }

    public record MinecraftProfileSummary(java.util.UUID uuid, String name, MinecraftProfile.MinecraftPlatform platform, boolean primary) {}
}
