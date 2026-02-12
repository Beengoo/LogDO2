package ua.beengoo.logdo2.plugin.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ua.beengoo.logdo2.api.entity.WebServerInfo;
import ua.beengoo.logdo2.plugin.LogDO2;
import ua.beengoo.logdo2.plugin.props.LogDO2PropertiesManager;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j(topic = "LogDO2")
public class Config {
    @Getter
    private static FileConfiguration fileConfiguration;
    private static LogDO2 plugin;

    public static void init(LogDO2 p) {
        plugin = p;
        updateConfigDefaults();
        fileConfiguration = p.getConfig();
        if (!LogDO2PropertiesManager.getINSTANCE().initFrom(plugin))
            throw new RuntimeException("Unable to initialize plugin configuration");
    }

    public static void reload() {
        fileConfiguration = plugin.getConfig();
        LogDO2PropertiesManager.getINSTANCE().reload(plugin);
    }

    public static WebServerInfo buildWebServerInfo(){
        return new WebServerInfo(
                fileConfiguration.getString("web.host", "localhost"),
                fileConfiguration.getInt("web.port", 3180),
                fileConfiguration.getString("web.publicUrl", "http://localhost:" + fileConfiguration.getInt("web.port", 8080)),
                "/login", "/login/callback"
        );
    }

    public static void updateConfigDefaults() {
        try {
            InputStream in = plugin.getResource("config.yml");
            if (in == null) return;
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.loadFromString(text);

            File file = new File(plugin.getDataFolder(), "config.yml");
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
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
            log.warn("Failed to merge default config", e);
        }
    }

}
