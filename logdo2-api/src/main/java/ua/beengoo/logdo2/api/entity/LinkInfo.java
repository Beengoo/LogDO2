package ua.beengoo.logdo2.api.entity;

public class LinkInfo implements BaseLinkInfo {
    private final DiscordProfile discordProfile;
    private final MinecraftProfile minecraftProfile;
    private final boolean isPrimary;

    public LinkInfo(DiscordProfile discordProfile, MinecraftProfile minecraftProfile, boolean isPrimary) {
        this.discordProfile = discordProfile;
        this.minecraftProfile = minecraftProfile;
        this.isPrimary = isPrimary;
    }

    @Override
    public DiscordProfile getDiscordProfile() {
        return discordProfile;
    }

    @Override
    public BaseMinecraftProfile getMinecraftProfile() {
        return minecraftProfile;
    }

    @Override
    public boolean isPrimary() {
        return isPrimary;
    }
}
