package ua.beengoo.logdo2.plugin.integration;

import java.lang.reflect.Method;
import java.util.UUID;

public final class FloodgateHook {
    private final boolean present;
    private final Object api;
    private final Method isFloodgatePlayer;
    private final Method isFloodgateId;

    public FloodgateHook() {
        Object inst = null;
        Method m1 = null, m2 = null;
        boolean ok = false;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            var getInstance = apiClass.getMethod("getInstance");
            inst = getInstance.invoke(null);
            try { m1 = apiClass.getMethod("isFloodgatePlayer", UUID.class); } catch (NoSuchMethodException ignore) {}
            try { m2 = apiClass.getMethod("isFloodgateId", UUID.class); } catch (NoSuchMethodException ignore) {}
            ok = inst != null && (m1 != null || m2 != null);
        } catch (Throwable ignore) {
            ok = false;
        }
        this.present = ok;
        this.api = inst;
        this.isFloodgatePlayer = m1;
        this.isFloodgateId = m2;
    }

    public boolean isPresent() { return present; }

    public boolean isBedrock(UUID uuid) {
        if (!present) return false;
        try {
            if (isFloodgatePlayer != null) return (boolean) isFloodgatePlayer.invoke(api, uuid);
            if (isFloodgateId != null)     return (boolean) isFloodgateId.invoke(api, uuid);
        } catch (Throwable ignore) { /* fallthrough */ }
        return false;
    }
}
