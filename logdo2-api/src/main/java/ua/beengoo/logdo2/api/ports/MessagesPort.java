package ua.beengoo.logdo2.api.ports;

import java.util.Map;

public interface MessagesPort {
    /** Повертає сирий рядок (для Discord/логів). Якщо ключа нема — повертає сам ключ. */
    String raw(String path);

    /** Рядок для Minecraft із кольорами (& → §). */
    String mc(String path);

    /** Рядок для Minecraft із плейсхолдерами {name}/{ip}/... та кольорами. */
    String mc(String path, Map<String, String> placeholders);
}
