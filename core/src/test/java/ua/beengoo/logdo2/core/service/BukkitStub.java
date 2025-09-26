package ua.beengoo.logdo2.core.service;

import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;

/**
 * Installs a minimal stub for {@link Bukkit} static access so {@link LoginService} tests
 * can exercise logic without a full server runtime.
 */
final class BukkitStub implements AutoCloseable {
    private final Object previous;

    private BukkitStub(Object previous) {
        this.previous = previous;
    }

    static BukkitStub install() {
        try {
            var field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            Object prev = field.get(null);
            field.set(null, createServerProxy());
            return new BukkitStub(prev);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to stub Bukkit server", e);
        }
    }

    private static Server createServerProxy() {
        InvocationHandler handler = new InvocationHandler() {
            private final Logger logger = Logger.getLogger("test-bukkit");

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                if (name.equals("isPrimaryThread")) {
                    return Boolean.TRUE;
                }
                if (name.equals("getLogger")) {
                    return logger;
                }
                if (name.equals("getPlayer")) {
                    return null; // tests do not require live players
                }
                if (name.equals("getPluginManager")) {
                    return createProxy(method.getReturnType());
                }
                if (name.equals("getScheduler") || name.equals("getGlobalRegionScheduler")) {
                    return createProxy(method.getReturnType());
                }
                return defaultValue(method.getReturnType());
            }
        };
        return (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class[]{Server.class}, handler);
    }

    private static Object createProxy(Class<?> iface) {
        return Proxy.newProxyInstance(iface.getClassLoader(), new Class[]{iface}, (proxy, method, args) -> {
            if (method.getReturnType() == Void.TYPE) {
                return null;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == Boolean.TYPE) return Boolean.FALSE;
        if (type == Byte.TYPE) return (byte) 0;
        if (type == Short.TYPE) return (short) 0;
        if (type == Integer.TYPE) return 0;
        if (type == Long.TYPE) return 0L;
        if (type == Float.TYPE) return 0f;
        if (type == Double.TYPE) return 0d;
        if (type == Character.TYPE) return '\0';
        return null;
    }

    @Override
    public void close() {
        try {
            var field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, previous);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to restore Bukkit server", e);
        }
    }
}
