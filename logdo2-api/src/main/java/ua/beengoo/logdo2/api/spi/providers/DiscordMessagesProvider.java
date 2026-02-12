package ua.beengoo.logdo2.api.spi.providers;

import java.util.UUID;

public interface DiscordMessagesProvider {
    /**
     * First message bot will send to user after authentication
    */
    void sendGreetingsMessage(long discordId, UUID mcUuid, String playerName);

    /**
     * Confirm message bot will send to user when IP was changed
     * */
    void sendLocationConfirmMessage(long discordId, UUID mcUuid, String playerName, String newIp);

    /**
     * For Bedrock player after they send login code to bot, reply with
     */
    void sendOAuth2URLMessage(long discordId, String url);
}
