package ua.beengoo.logdo2.api.ports;

import java.util.UUID;

public interface DiscordDmPort {
    /** First account link message */
    void sendFirstLoginDm(long discordId, UUID mcUuid, String playerName, String publicUrl);

    /** IP Confirm message */
    void sendIpConfirmDm(long discordId, UUID mcUuid, String playerName, String newIp);

    /** For Bedrock: Send oAuth2 link in DM */
    void sendFinalizeOAuthLink(long discordId, String url);
}
