package ua.beengoo.logdo2.plugin.adapters.jdbc;

import ua.beengoo.logdo2.api.ports.ProfileRepo;
import ua.beengoo.logdo2.plugin.db.DatabaseManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class JdbcProfileRepo implements ProfileRepo {
    private final DataSource ds;
    private final DatabaseManager.Dialect dialect;

    public JdbcProfileRepo(DataSource ds, DatabaseManager.Dialect dialect) {
        this.ds = ds;
        this.dialect = dialect;
        ensurePlatformColumn();
    }

    private void ensurePlatformColumn() {
        String alter = switch (dialect) {
            case POSTGRES, SQLITE -> "ALTER TABLE mc_profiles ADD COLUMN platform TEXT";
            case MYSQL -> "ALTER TABLE mc_profiles ADD COLUMN platform VARCHAR(16)";
        };
        try (Connection c = ds.getConnection(); var st = c.createStatement()) {
            st.execute(alter);
        } catch (Exception ignored) { /* вже існує — ок */ }
    }

    @Override
    public Optional<UUID> findUuidByName(String name) {
        String sql = "SELECT mc_uuid FROM mc_profiles WHERE LOWER(name)=LOWER(?) LIMIT 1";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(UUID.fromString(rs.getString(1)));
                return Optional.empty();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    public Optional<String> findNameByUuid(UUID uuid) {
        String sql = "SELECT name FROM mc_profiles WHERE mc_uuid=? LIMIT 1";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return Optional.ofNullable(rs.getString(1));
                return Optional.empty();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    public Optional<String> findLastConfirmedIp(UUID profileUuid) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT last_ip FROM mc_profiles WHERE mc_uuid=?")) {
            ps.setString(1, profileUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.ofNullable(rs.getString(1));
                return Optional.empty();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    public void updateLastConfirmedIp(UUID profileUuid, String ip) {
        long now = Instant.now().getEpochSecond();
        String sql = switch (dialect) {
            case POSTGRES, SQLITE -> """
                INSERT INTO mc_profiles(mc_uuid,name,last_ip,updated_at)
                VALUES(?,?,?,?)
                ON CONFLICT(mc_uuid) DO UPDATE SET last_ip=EXCLUDED.last_ip, updated_at=EXCLUDED.updated_at
            """;
            case MYSQL -> """
                INSERT INTO mc_profiles(mc_uuid,name,last_ip,updated_at)
                VALUES(?,?,?,?)
                ON DUPLICATE KEY UPDATE last_ip=VALUES(last_ip), updated_at=VALUES(updated_at)
            """;
        };
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, profileUuid.toString());
            ps.setString(2, null);
            ps.setString(3, ip);
            ps.setLong(4, now);
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    public void upsertName(UUID profileUuid, String playerName) {
        long now = Instant.now().getEpochSecond();
        String sql = switch (dialect) {
            case POSTGRES, SQLITE -> """
                INSERT INTO mc_profiles(mc_uuid,name,updated_at)
                VALUES(?,?,?)
                ON CONFLICT(mc_uuid) DO UPDATE SET name=EXCLUDED.name, updated_at=EXCLUDED.updated_at
            """;
            case MYSQL -> """
                INSERT INTO mc_profiles(mc_uuid,name,updated_at)
                VALUES(?,?,?)
                ON DUPLICATE KEY UPDATE name=VALUES(name), updated_at=VALUES(updated_at)
            """;
        };
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, profileUuid.toString());
            ps.setString(2, playerName);
            ps.setLong(3, now);
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    public void updatePlatform(UUID profileUuid, String platform) {
        long now = Instant.now().getEpochSecond();
        String sql = switch (dialect) {
            case POSTGRES, SQLITE -> """
                INSERT INTO mc_profiles(mc_uuid,platform,updated_at)
                VALUES(?,?,?)
                ON CONFLICT(mc_uuid) DO UPDATE SET platform=EXCLUDED.platform, updated_at=EXCLUDED.updated_at
            """;
            case MYSQL -> """
                INSERT INTO mc_profiles(mc_uuid,platform,updated_at)
                VALUES(?,?,?)
                ON DUPLICATE KEY UPDATE platform=VALUES(platform), updated_at=VALUES(updated_at)
            """;
        };
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, profileUuid.toString());
            ps.setString(2, platform);
            ps.setLong(3, now);
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
