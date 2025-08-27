package ua.beengoo.logdo2.plugin.adapters.jdbc;

import ua.beengoo.logdo2.api.ports.BanProgressRepo;
import ua.beengoo.logdo2.plugin.db.DatabaseManager;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;

public class JdbcBanProgressRepo implements BanProgressRepo {
    private final DataSource ds;
    private final DatabaseManager.Dialect dialect;

    public JdbcBanProgressRepo(DataSource ds, DatabaseManager.Dialect dialect) {
        this.ds = ds;
        this.dialect = dialect;
        ensureTable();
    }

    private void ensureTable() {
        String sql = switch (dialect) {
            case POSTGRES, SQLITE -> """
                CREATE TABLE IF NOT EXISTS ban_progress (
                  ip             TEXT PRIMARY KEY,
                  attempts       INTEGER NOT NULL,
                  last_attempt   BIGINT  NOT NULL,
                  last_ban_until BIGINT  NOT NULL
                )
            """;
            case MYSQL -> """
                CREATE TABLE IF NOT EXISTS ban_progress (
                  ip             VARCHAR(45) PRIMARY KEY,
                  attempts       INT     NOT NULL,
                  last_attempt   BIGINT  NOT NULL,
                  last_ban_until BIGINT  NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;
        };
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure ban_progress table: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Record> findByIp(String ip) {
        String q = "SELECT attempts, last_attempt, last_ban_until FROM ban_progress WHERE ip=?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(q)) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Record(ip, rs.getInt(1), rs.getLong(2), rs.getLong(3)));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void upsert(String ip, int attempts, long lastAttemptEpochSec, long lastBanUntilEpochSec) {
        String upsert = """
            INSERT INTO ban_progress(ip, attempts, last_attempt, last_ban_until)
            VALUES(?,?,?,?)
            ON CONFLICT(ip) DO UPDATE SET
              attempts=EXCLUDED.attempts,
              last_attempt=EXCLUDED.last_attempt,
              last_ban_until=EXCLUDED.last_ban_until
        """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(upsert)) {
            ps.setString(1, ip);
            ps.setInt(2, attempts);
            ps.setLong(3, lastAttemptEpochSec);
            ps.setLong(4, lastBanUntilEpochSec);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Fallback для MySQL
            String mysql = """
                INSERT INTO ban_progress(ip, attempts, last_attempt, last_ban_until)
                VALUES(?,?,?,?)
                ON DUPLICATE KEY UPDATE
                  attempts=VALUES(attempts),
                  last_attempt=VALUES(last_attempt),
                  last_ban_until=VALUES(last_ban_until)
            """;
            try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(mysql)) {
                ps.setString(1, ip);
                ps.setInt(2, attempts);
                ps.setLong(3, lastAttemptEpochSec);
                ps.setLong(4, lastBanUntilEpochSec);
                ps.executeUpdate();
            } catch (SQLException e2) {
                throw new RuntimeException(e2);
            }
        }
    }

    @Override
    public void reset(String ip) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM ban_progress WHERE ip=?")) {
            ps.setString(1, ip);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
