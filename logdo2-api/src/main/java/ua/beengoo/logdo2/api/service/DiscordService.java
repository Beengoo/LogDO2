package ua.beengoo.logdo2.api.service;

import net.dv8tion.jda.api.JDA;

/**
 * Discord-related read surface. Implementations should avoid direct bot manipulation
 * and act as a lightweight provider of JDA instance and guild configuration.
 */
public interface DiscordService {
    JDA getDiscordBot();
    String getTargetGuildId();
}
