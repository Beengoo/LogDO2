package ua.beengoo.logdo2.api.entity;

public interface BaseLinkInfo {
    BaseDiscordProfile getDiscordProfile();
    BaseMinecraftProfile getMinecraftProfile();
    boolean isPrimary();
}
