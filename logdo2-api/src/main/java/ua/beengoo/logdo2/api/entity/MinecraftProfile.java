package ua.beengoo.logdo2.api.entity;

import org.bukkit.OfflinePlayer;

public class MinecraftProfile implements BaseMinecraftProfile{
    @Override
    public OfflinePlayer getPlayer() {
        return null;
    }

    @Override
    public String getLastValidIp() {
        return "";
    }
}
