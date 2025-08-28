package ua.beengoo.logdo2.plugin.util;

import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Simple audit logger that writes to a dedicated file in the server root. */
public class AuditLogger implements Closeable {
    private final File file;
    private final Writer writer;
    private final Object lock = new Object();
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    public AuditLogger(Plugin plugin, String filename) throws IOException {
        File pluginsDir = plugin.getDataFolder().getParentFile(); // .../plugins
        File root = (pluginsDir != null && pluginsDir.getParentFile() != null)
                ? pluginsDir.getParentFile()
                : new File(".").getCanonicalFile();
        this.file = new File(root, filename);
        this.writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(this.file, true), StandardCharsets.UTF_8));
    }

    public void log(String category, String action, Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append(TS.format(Instant.now())).append(" ")
          .append("[").append(safe(category)).append("] ")
          .append(safe(action));
        if (fields != null && !fields.isEmpty()) {
            boolean first = true;
            sb.append(" ");
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (!first) sb.append(" ");
                first = false;
                sb.append(safe(e.getKey())).append("=").append(quoteIfNeeded(safe(e.getValue())));
            }
        }
        String line = sb.toString();
        synchronized (lock) {
            try {
                writer.write(line);
                writer.write('\n');
                writer.flush();
            } catch (IOException ignored) { }
        }
    }

    private static String safe(String s) {
        if (s == null) return "";
        // Strip newlines and tabs
        return s.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    }

    private static String quoteIfNeeded(String s) {
        if (s == null || s.isEmpty()) return "\"\"";
        if (s.indexOf(' ') >= 0) return '"' + s.replace("\"", "\\\"") + '"';
        return s;
    }

    @Override
    public void close() throws IOException {
        synchronized (lock) { writer.close(); }
    }
}

