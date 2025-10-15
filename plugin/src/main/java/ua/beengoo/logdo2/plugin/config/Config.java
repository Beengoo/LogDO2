package ua.beengoo.logdo2.plugin.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.configuration.file.FileConfiguration;
import ua.beengoo.logdo2.plugin.LogDO2;
import ua.beengoo.logdo2.plugin.props.LogDO2PropertiesManager;

@Slf4j
public class Config {
    @Getter
    private static FileConfiguration fileConfiguration;
    private static LogDO2 plugin;

    public static void init(LogDO2 p) {
        plugin = p;
        updateConfigDefaults();
        fileConfiguration = p.getConfig();
        LogDO2PropertiesManager.getINSTANCE().initFrom(plugin);
    }

    public static void reload() {
        fileConfiguration = plugin.getConfig();
        LogDO2PropertiesManager.getINSTANCE().reload(plugin);
    }



    public static void updateConfigDefaults() {
        try {
            java.io.InputStream in = plugin.getResource("config.yml");
            if (in == null) return;
            String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            org.bukkit.configuration.file.YamlConfiguration defaults = new org.bukkit.configuration.file.YamlConfiguration();
            defaults.loadFromString(text);

            java.io.File file = new java.io.File(plugin.getDataFolder(), "config.yml");
            org.bukkit.configuration.file.YamlConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                // Only add keys that are truly missing from file (ignore defaults)
                if (!cfg.isSet(key)) {
                    cfg.set(key, defaults.get(key));
                    changed = true;
                }
            }
            if (changed) {
                cfg.save(file);
                plugin.reloadConfig();
            }
        } catch (Exception e) {
            log.warn("Failed to marge default config", e);
        }
    }

}
