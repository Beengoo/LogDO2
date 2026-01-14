package ua.beengoo.logdo2.plugin.adapters.floodgate;

import org.geysermc.floodgate.api.FloodgateApi;
import ua.beengoo.logdo2.api.spi.providers.FloodgateProvider;

import java.util.UUID;

public class FloodgateAdapter implements FloodgateProvider {
    private final FloodgateApi floodgateApi;
    private final boolean available;

    public FloodgateAdapter() {
        FloodgateApi api = null;
        boolean isAvailable = false;
        try {
            api = FloodgateApi.getInstance();
            isAvailable = api != null;
        } catch (Throwable ignored) {
        }
        this.floodgateApi = api;
        this.available = isAvailable;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public boolean isBedrockPlayer(UUID uuid) {
        if (!available || floodgateApi == null) {
            return false;
        }
        try {
            return floodgateApi.isFloodgatePlayer(uuid);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
