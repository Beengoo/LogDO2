package ua.beengoo.logdo2.plugin.props;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.plugin.java.JavaPlugin;
import ua.beengoo.logdo2.api.spi.providers.Properties;
import ua.beengoo.logdo2.api.spi.providers.PropertiesProvider;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Slf4j(topic = "LogDO2")
public class LogDO2PropertiesManager implements PropertiesProvider {
    @Getter
    private static final LogDO2PropertiesManager INSTANCE = new LogDO2PropertiesManager();
    private final AtomicReference<Properties> current = new AtomicReference<>();
    private final List<Consumer<Properties>> listeners = new CopyOnWriteArrayList<>();

    private LogDO2PropertiesManager() {}

    @Override
    public Properties getSnapshot() {
        return current.get();
    }

    @Override
    public void addListener(Consumer<Properties> listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(Consumer<Properties> listener) {
        listeners.remove(listener);
    }

    public boolean initFrom(JavaPlugin plugin) {
        return reload(plugin);
    }

    public boolean reload(JavaPlugin plugin) {
        try {
            plugin.reloadConfig();
            var cfg = plugin.getConfig();

            long bedrockCodeTimeAfterLeave = cfg.getLong("platform.bedrockCodeTimeAfterLeave");
            int bedrockLimitPerDiscord = cfg.getInt("limits.perDiscord.bedrock", 1);
            int javaLimitPerDiscord = cfg.getInt("limits.perDiscord.java", 1);
            boolean limitIncludeReserved = cfg.getBoolean("limits.perDiscord.includeReserved", false);
            boolean bansEnabled = cfg.getBoolean("bans.enabled", true);
            long banTrackWindowSec = cfg.getLong("bans.trackWindowSeconds", 2592000L);
            double banMultiplier = cfg.getDouble("bans.multiplier", 2.0);
            double banBaseSec = cfg.getLong("bans.baseSeconds", 1800L);
            long banMaxSec = cfg.getLong("bans.maxSeconds", 604800L);
            boolean disallowSimultaneousPlay = cfg.getBoolean("limits.perDiscord.disallowSimultaneousPlay", false);

            Properties newProps = new Properties(bedrockCodeTimeAfterLeave, bedrockLimitPerDiscord, javaLimitPerDiscord,
                    limitIncludeReserved, bansEnabled, banTrackWindowSec, banMultiplier, banBaseSec, banMaxSec,
                    disallowSimultaneousPlay);

            current.set(newProps);
            for (var l : listeners) {
                try { l.accept(newProps); } catch (Exception ignored) {}
            }
            log.info("Applying configuration");
            return true;
        } catch (Exception e) {
            log.error("Failed to load configuration", e);
            return false;
        }
    }
}
