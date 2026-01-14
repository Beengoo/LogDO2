package ua.beengoo.logdo2.api.entity;

import ua.beengoo.logdo2.api.spi.repo.AccountsRepo;
import ua.beengoo.logdo2.api.spi.repo.DiscordUserRepo;
import ua.beengoo.logdo2.api.spi.repo.ProfileRepo;
import ua.beengoo.logdo2.api.spi.repo.TokensRepo;

import java.util.*;

/**
 * Factory for creating LogDO2Profile entities by aggregating data from multiple repositories.
 */
public class LogDO2ProfileFactory {

    /**
     * Create a LogDO2Profile from a Discord ID
     * @param discordId The Discord user ID
     * @param accountsRepo Accounts repository
     * @param profileRepo Profile repository
     * @param tokensRepo Tokens repository
     * @param discordUserRepo Discord user repository
     * @return Optional containing the profile, or empty if Discord user not found
     */
    public static Optional<LogDO2Profile> fromDiscordId(
            long discordId,
            AccountsRepo accountsRepo,
            ProfileRepo profileRepo,
            TokensRepo tokensRepo,
            DiscordUserRepo discordUserRepo) {

        // Build Discord OAuth info with created_at
        DiscordOAuthInfo oauthInfo = buildDiscordOAuthInfo(discordId, tokensRepo, discordUserRepo);
        DiscordProfile discordProfile = new DiscordProfile(oauthInfo);

        // Find all linked Minecraft profiles with primary flag
        List<LinkInfo> linkInfoList = new ArrayList<>();
        Map<UUID, Boolean> linkedProfiles = accountsRepo.findLinksWithPrimaryFlag(discordId);

        for (Map.Entry<UUID, Boolean> entry : linkedProfiles.entrySet()) {
            UUID mcUuid = entry.getKey();
            boolean isPrimary = entry.getValue();

            Optional<MinecraftProfile> mcProfile = buildMinecraftProfile(mcUuid, profileRepo);
            mcProfile.ifPresent(profile ->
                linkInfoList.add(new LinkInfo(discordProfile, profile, isPrimary))
            );
        }

        // Determine profile status
        LogDO2ProfileStatus status = fetchProfileStatus(discordProfile, linkInfoList);

        // Get real profile_id from database, or fallback to discord_id
        String profileId = discordUserRepo.findProfileIdByDiscordId(discordId)
                .orElse(String.valueOf(discordId));

        return Optional.of(new LogDO2Profile(profileId, discordProfile, status, linkInfoList));
    }

    /**
     * Create a LogDO2Profile from a Minecraft UUID
     * @param minecraftUuid The Minecraft player UUID
     * @param accountsRepo Accounts repository
     * @param profileRepo Profile repository
     * @param tokensRepo Tokens repository
     * @param discordUserRepo Discord user repository
     * @return Optional containing the profile, or empty if player not linked
     */
    public static Optional<LogDO2Profile> fromMinecraftUuid(
            UUID minecraftUuid,
            AccountsRepo accountsRepo,
            ProfileRepo profileRepo,
            TokensRepo tokensRepo,
            DiscordUserRepo discordUserRepo) {

        // Find linked Discord ID
        Optional<Long> discordIdOpt = accountsRepo.findDiscordForProfile(minecraftUuid);
        if (discordIdOpt.isEmpty()) {
            return Optional.empty();
        }

        // Use fromDiscordId to build the complete profile
        return fromDiscordId(discordIdOpt.get(), accountsRepo, profileRepo, tokensRepo, discordUserRepo);
    }

    /**
     * Build Discord OAuth info from tokens repository and discord user repo
     */
    private static DiscordOAuthInfo buildDiscordOAuthInfo(long discordId, TokensRepo tokensRepo, DiscordUserRepo discordUserRepo) {
        Optional<TokensRepo.TokenView> tokenView = tokensRepo.find(discordId);
        long createdAt = discordUserRepo.findCreatedAtByDiscordId(discordId).orElse(0L);

        if (tokenView.isPresent()) {
            TokensRepo.TokenView token = tokenView.get();
            return new DiscordOAuthInfo(
                discordId,
                token.accessToken(),
                token.refreshToken(),
                token.tokenType(),
                token.scope(),
                token.expiresAt().getEpochSecond(),
                System.currentTimeMillis() / 1000, // Current time as updatedAt
                createdAt
            );
        }

        // Return empty OAuth info if no tokens found
        return new DiscordOAuthInfo(discordId, "", "", "", "", 0L, 0L, createdAt);
    }

    /**
     * Build Minecraft profile from profile repository
     */
    private static Optional<MinecraftProfile> buildMinecraftProfile(UUID uuid, ProfileRepo profileRepo) {
        Optional<String> nameOpt = profileRepo.findNameByUuid(uuid);
        Optional<String> platformOpt = profileRepo.findPlatform(uuid);
        Optional<String> lastIpOpt = profileRepo.findLastConfirmedIp(uuid);

        return Optional.of(new MinecraftProfile(
                uuid,
                nameOpt.orElse("Unknown"),
                platformOpt.orElse("JAVA"),
                lastIpOpt.orElse("")
        ));
    }

    /**
     * Determine profile status based on Discord and Minecraft link information
     */
    private static LogDO2ProfileStatus fetchProfileStatus(
            DiscordProfile discordProfile,
            List<LinkInfo> linkInfoList) {

        // If user is not authenticated
        if (!discordProfile.isUserAuthenticated()) {
            return LogDO2ProfileStatus.UNAUTHORIZED;
        }

        // If authorized and has linked accounts, they're authorized
        if (!linkInfoList.isEmpty()) {
            return LogDO2ProfileStatus.AUTHORIZED;
        }

        // Authorized but no linked accounts yet
        return LogDO2ProfileStatus.UNAUTHORIZED;
    }
}
