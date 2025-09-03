package ua.beengoo.logdo2.api.ports;

import java.util.Map;

public interface MessagesPort {

    String raw(String path);

    String mc(String path);

    String mc(String path, Map<String, String> placeholders);
}
