package ua.beengoo.logdo2.plugin.i18n;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import ua.beengoo.logdo2.api.ports.MessagesPort;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class YamlMessages implements MessagesPort {
    private final Plugin plugin;
    private final File file;
    private FileConfiguration cfg;
    private FileConfiguration defaults;

    public YamlMessages(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        ensureFile();
        reload();
    }

    private void ensureFile() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
    }

    public final void reload() {
        try (InputStream in = plugin.getResource("messages.yml")) {
            defaults = new YamlConfiguration();
            if (in != null) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                defaults.loadFromString(text);
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Failed to load default messages: " + e.getMessage());
        }
        cfg = YamlConfiguration.loadConfiguration(file);
        if (defaults != null) {
            cfg.setDefaults(defaults);
            cfg.options().copyDefaults(true);
        }
    }

    @Override
    public String raw(String path) {
        String s = cfg.getString(path);
        if (s == null && defaults != null) s = defaults.getString(path);
        return s == null ? path : s;
    }

    @Override
    public String mc(String path) {
        return colorize(raw(path));
    }

    @Override
    public String mc(String path, Map<String, String> placeholders) {
        String s = raw(path);
        if (placeholders != null) {
            for (var e : placeholders.entrySet()) {
                s = s.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
            }
        }
        return colorize(s);
    }

    private static String colorize(String s) {
        return s == null ? "" : s.replace('&', '§');
    }
}
