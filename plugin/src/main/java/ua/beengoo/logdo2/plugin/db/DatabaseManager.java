package ua.beengoo.logdo2.plugin.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class DatabaseManager {
    public enum Dialect { SQLITE, MYSQL, POSTGRES }

    private final Plugin plugin;
    private HikariDataSource ds;
    private Dialect dialect;

    public DatabaseManager(Plugin plugin) { this.plugin = plugin; }

    public void start() {
        String url = plugin.getConfig().getString("database.url");
        String driver = plugin.getConfig().getString("database.driver", "");
        String user = plugin.getConfig().getString("database.username", "");
        String pass = plugin.getConfig().getString("database.password", "");
        int maxPool = plugin.getConfig().getInt("database.pool.maxPoolSize", 8);

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        if (user != null && !user.isBlank()) cfg.setUsername(user);
        if (pass != null && !pass.isBlank()) cfg.setPassword(pass);
        cfg.setMaximumPoolSize(maxPool);
        // легкий keepalive
        cfg.setKeepaliveTime(30_000);
        cfg.setConnectionTimeout(15_000);

        this.ds = new HikariDataSource(cfg);
        this.dialect = detectDialect(url, driver);

        if (this.dialect == Dialect.SQLITE) {
            // вмикаємо FK
            try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
                st.execute("PRAGMA foreign_keys=ON");
            } catch (Exception ignored) {}
        }

        runMigrations();
        plugin.getLogger().info("DB ready (" + dialect + ")");
    }

    public void stop() {
        if (ds != null) ds.close();
    }

    public DataSource dataSource() { return ds; }

    public Dialect dialect() { return dialect; }

    private static Dialect detectDialect(String url, String driverHint) {
        String u = (url == null ? "" : url).toLowerCase(Locale.ROOT);
        String d = (driverHint == null ? "" : driverHint).toLowerCase(Locale.ROOT);
        if (u.startsWith("jdbc:sqlite:") || d.contains("sqlite")) return Dialect.SQLITE;
        if (u.startsWith("jdbc:mysql:")  || d.contains("mysql"))  return Dialect.MYSQL;
        if (u.startsWith("jdbc:postgresql:") || d.contains("postgres")) return Dialect.POSTGRES;
        throw new IllegalStateException("Unknown database dialect for url=" + url);
    }

    private void runMigrations() {
        String path = switch (dialect) {
            case SQLITE   -> "db/migration/sqlite/V1__init.sql";
            case POSTGRES -> "db/migration/postgres/V1__init.sql";
            case MYSQL    -> "db/migration/mysql/V1__init.sql";
        };
        try (var in = java.util.Objects.requireNonNull(
                plugin.getResource(path), "Migration file not found: " + path
        )) {
            String sql = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .lines().collect(java.util.stream.Collectors.joining("\n"));
            try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
                // просте розбиття по ';' на кінці рядка
                for (String stmt : sql.split(";\\s*\\n")) {
                    String s = stmt.trim();
                    if (s.isEmpty()) continue;
                    st.execute(s);
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("Migration failed (" + path + "): " + ex.getMessage(), ex);
        }
    }


    public static long nowEpoch() { return Instant.now().getEpochSecond(); }
}
