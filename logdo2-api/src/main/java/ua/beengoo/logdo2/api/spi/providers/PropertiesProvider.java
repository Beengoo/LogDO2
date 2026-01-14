package ua.beengoo.logdo2.api.spi.providers;

import java.util.function.Consumer;

/**
 * Interface for dynamic logdo2 properties
*/
public interface PropertiesProvider {
    Properties getSnapshot();

    default void addListener(Consumer<Properties> listener) {
        throw new UnsupportedOperationException("Listeners not implemented");
    }

    default void removeListener(Consumer<Properties> listener) {
        throw new UnsupportedOperationException("Listeners not implemented");
    }
}
