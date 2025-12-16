package ua.beengoo.logdo2.api.entity;

import org.bukkit.OfflinePlayer;

public interface BaseMinecraftProfile {
    OfflinePlayer getPlayer();

    /**
     * @return Last validated player IP address.
     */
    String getLastValidIp();
}
