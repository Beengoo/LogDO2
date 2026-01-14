package ua.beengoo.logdo2.api.spi.providers;

import java.util.Map;

public interface MessagesProvider {

    String raw(String path);

    String mc(String path);

    String mc(String path, Map<String, String> placeholders);
}
