package ua.beengoo.logdo2.api.entity;

import java.util.UUID;

public class MinecraftProfile implements BaseMinecraftProfile {
    private final UUID uuid;
    private final String name;
    private final String platform;
    private final String lastValidIp;

    public MinecraftProfile(UUID uuid, String name, String platform, String lastValidIp) {
        this.uuid = uuid;
        this.name = name;
        this.platform = platform;
        this.lastValidIp = lastValidIp;
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPlatform() {
        return platform;
    }

    @Override
    public String getLastValidIp() {
        return lastValidIp;
    }
}
