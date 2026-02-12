package ua.beengoo.logdo2.plugin.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.plugin.Plugin;
import ua.beengoo.logdo2.api.entity.ProfileIdGenerator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

@Slf4j(topic = "LogDO2")
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
        if (!user.isBlank()) cfg.setUsername(user);
        if (!pass.isBlank()) cfg.setPassword(pass);
        cfg.setMaximumPoolSize(maxPool);
        cfg.setKeepaliveTime(30_000);
        cfg.setConnectionTimeout(15_000);

        this.ds = new HikariDataSource(cfg);
        this.dialect = detectDialect(url, driver);

        if (this.dialect == Dialect.SQLITE) {
            try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
                st.execute("PRAGMA foreign_keys=ON");
            } catch (Exception ignored) {}
        }

        runMigrations();
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
        if (dialect == Dialect.SQLITE) {
            runSqliteMigrations();
        } else {
            runStandardMigrations();
        }
        backfillProfileIds();
    }

    private void runStandardMigrations() {
        String path = switch (dialect) {
            case POSTGRES -> "db/migration/postgres/V1__init.sql";
            case MYSQL    -> "db/migration/mysql/V1__init.sql";
            default -> throw new IllegalStateException("Unexpected dialect: " + dialect);
        };
        try (var in = java.util.Objects.requireNonNull(
                plugin.getResource(path), "Migration file not found: " + path
        )) {
            String sql = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .lines().collect(java.util.stream.Collectors.joining("\n"));
            try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
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

    private void runSqliteMigrations() {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            // Helper method to check if column exists
            java.util.function.BiPredicate<Connection, String> columnExists = (conn, colName) -> {
                try (var rs = conn.createStatement().executeQuery("PRAGMA table_info(discord_accounts)")) {
                    while (rs.next()) {
                        if (colName.equals(rs.getString("name"))) return true;
                    }
                } catch (Exception ignored) {}
                return false;
            };
            st.execute("""
                CREATE TABLE IF NOT EXISTS discord_accounts (
                  discord_id         INTEGER PRIMARY KEY,
                  username           TEXT,
                  global_name        TEXT,
                  email              TEXT,
                  avatar_hash        TEXT,
                  commands_installed INTEGER NOT NULL DEFAULT 0,
                  updated_at         BIGINT NOT NULL
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS oauth_tokens (
                  discord_id   INTEGER PRIMARY KEY,
                  access_enc   BLOB NOT NULL,
                  refresh_enc  BLOB NOT NULL,
                  token_type   TEXT NOT NULL,
                  scope        TEXT NOT NULL,
                  expires_at   BIGINT NOT NULL,
                  updated_at   BIGINT NOT NULL,
                  FOREIGN KEY (discord_id) REFERENCES discord_accounts(discord_id) ON DELETE CASCADE
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS mc_profiles (
                  mc_uuid     TEXT PRIMARY KEY,
                  name        TEXT,
                  last_ip     TEXT,
                  platform    TEXT,
                  updated_at  BIGINT NOT NULL
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS links (
                  discord_id  INTEGER NOT NULL,
                  mc_uuid     TEXT    NOT NULL,
                  active      INTEGER NOT NULL DEFAULT 1,
                  created_at  BIGINT  NOT NULL,
                  PRIMARY KEY (discord_id, mc_uuid),
                  FOREIGN KEY (discord_id) REFERENCES discord_accounts(discord_id) ON DELETE CASCADE,
                  FOREIGN KEY (mc_uuid)    REFERENCES mc_profiles(mc_uuid)      ON DELETE CASCADE
                )""");

            st.execute("CREATE INDEX IF NOT EXISTS idx_links_discord ON links(discord_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_links_mc ON links(mc_uuid)");

            // Add columns to discord_accounts if they don't exist
            if (!columnExists.test(c, "created_at")) {
                st.execute("ALTER TABLE discord_accounts ADD COLUMN created_at BIGINT");
            }
            if (!columnExists.test(c, "profile_id")) {
                st.execute("ALTER TABLE discord_accounts ADD COLUMN profile_id TEXT");
            }

            // Add is_primary to links if it doesn't exist
            java.util.function.BiPredicate<Connection, String> linksColumnExists = (conn, colName) -> {
                try (var rs = conn.createStatement().executeQuery("PRAGMA table_info(links)")) {
                    while (rs.next()) {
                        if (colName.equals(rs.getString("name"))) return true;
                    }
                } catch (Exception ignored) {}
                return false;
            };

            if (!linksColumnExists.test(c, "is_primary")) {
                st.execute("ALTER TABLE links ADD COLUMN is_primary INTEGER NOT NULL DEFAULT 0");
            }

            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_discord_profile_id ON discord_accounts(profile_id)");

            // Add requires_reauth to discord_accounts if it doesn't exist
            if (!columnExists.test(c, "requires_reauth")) {
                st.execute("ALTER TABLE discord_accounts ADD COLUMN requires_reauth INTEGER NOT NULL DEFAULT 0");
            }

            // Initialize data
            st.execute("UPDATE discord_accounts SET created_at = updated_at WHERE created_at IS NULL");

            // Set first link as primary (idempotent)
            st.execute("""
                WITH first_links AS (
                    SELECT discord_id, mc_uuid, MIN(created_at) as min_created
                    FROM links
                    WHERE active = 1
                    GROUP BY discord_id
                )
                UPDATE links
                SET is_primary = 1
                WHERE is_primary = 0 AND EXISTS (
                    SELECT 1 FROM first_links
                    WHERE links.discord_id = first_links.discord_id
                      AND links.mc_uuid = first_links.mc_uuid
                      AND links.created_at = first_links.min_created
                )""");

            log.info("SQLite migrations completed successfully");
        } catch (Exception ex) {
            throw new RuntimeException("SQLite migration failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Backfill profile_id for existing rows that don't have one.
     * Uses Java ProfileIdGenerator to ensure consistent URL-safe Base64 encoding.
     */
    private void backfillProfileIds() {
        try (Connection c = ds.getConnection()) {
            // Find all discord_accounts without profile_id
            String selectSql = "SELECT discord_id, created_at, updated_at FROM discord_accounts WHERE profile_id IS NULL";
            String updateSql = "UPDATE discord_accounts SET profile_id = ? WHERE discord_id = ?";

            int updated = 0;
            try (PreparedStatement selectStmt = c.prepareStatement(selectSql);
                 PreparedStatement updateStmt = c.prepareStatement(updateSql);
                 ResultSet rs = selectStmt.executeQuery()) {

                while (rs.next()) {
                    long discordId = rs.getLong("discord_id");
                    Long createdAt = rs.getObject("created_at", Long.class);
                    long updatedAt = rs.getLong("updated_at");

                    // Use created_at if available, otherwise fall back to updated_at
                    long timestamp = (createdAt != null) ? createdAt : updatedAt;

                    // Generate profile_id using Java code (URL-safe Base64)
                    String profileId = ProfileIdGenerator.generate(discordId, timestamp);

                    updateStmt.setString(1, profileId);
                    updateStmt.setLong(2, discordId);
                    updateStmt.addBatch();
                    updated++;
                }

                if (updated > 0) {
                    updateStmt.executeBatch();
                    log.info("Backfilled {} profile_id values using Base64 URL-safe encoding", updated);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to backfill profile_id values: {}", ex.getMessage());
            // Don't throw - this is a best-effort migration
        }
    }
}
