package ua.beengoo.logdo2.plugin.adapters.jdbc;

import ua.beengoo.logdo2.api.entity.ProfileIdGenerator;
import ua.beengoo.logdo2.api.spi.repo.AccountsRepo;
import ua.beengoo.logdo2.plugin.db.DatabaseManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

public class JdbcAccountsRepo implements AccountsRepo {
    private final DataSource ds;
    private final DatabaseManager.Dialect dialect;

    public JdbcAccountsRepo(DataSource ds, DatabaseManager.Dialect dialect) {
        this.ds = ds; this.dialect = dialect;
    }

    @Override
    public void unlinkByProfile(UUID profileUuid) {
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM links WHERE mc_uuid=?")) {
                ps.setString(1, profileUuid.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps2 = c.prepareStatement("DELETE FROM mc_profiles WHERE mc_uuid=?")) {
                ps2.setString(1, profileUuid.toString());
                ps2.executeUpdate();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    public void unlinkByDiscord(long discordId) {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("DELETE FROM links WHERE discord_id=?")) {
            ps.setLong(1, discordId);
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    public void unlinkByDiscordAndProfile(long discordId, UUID profileUuid) {
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM links WHERE discord_id=? AND mc_uuid=?")) {
                ps.setLong(1, discordId);
                ps.setString(2, profileUuid.toString());
                ps.executeUpdate();
            }
            // Also remove the profile row for that UUID
            try (PreparedStatement ps2 = c.prepareStatement("DELETE FROM mc_profiles WHERE mc_uuid=?")) {
                ps2.setString(1, profileUuid.toString());
                ps2.executeUpdate();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    public void link(long discordId, UUID profileUuid) {
        reserve(discordId, profileUuid);
        activate(discordId, profileUuid);
    }

    @Override
    public void reserve(long discordId, UUID profileUuid) {
        long now = Instant.now().getEpochSecond();
        try (Connection c = ds.getConnection()) {
            // ensure mc_profile exists
            try (PreparedStatement ps = upsertProfileSql(c)) {
                ps.setString(1, profileUuid.toString());
                ps.setString(2, null);
                ps.setLong(3, now);
                if (dialect == DatabaseManager.Dialect.MYSQL) {
                    ps.setString(4, null);
                    ps.setLong(5, now);
                }
                ps.executeUpdate();
            }
            // ensure discord_account exists with created_at and profile_id
            String profileId = ProfileIdGenerator.generate(discordId, now);
            try (PreparedStatement ps = upsertAccountSql(c)) {
                ps.setLong(1, discordId);
                ps.setString(2, null);
                ps.setString(3, null);
                ps.setString(4, null);
                ps.setString(5, null);
                ps.setLong(6, now); // created_at
                ps.setLong(7, now); // updated_at
                ps.setString(8, profileId);
                if (dialect == DatabaseManager.Dialect.MYSQL) {
                    ps.setString(9, null);
                    ps.setString(10, null);
                    ps.setString(11, null);
                    ps.setString(12, null);
                    ps.setLong(13, now); // created_at
                    ps.setLong(14, now); // updated_at
                    ps.setString(15, profileId);
                }
                ps.executeUpdate();
            }
            // link (reserve: active=0, is_primary=0)
            String sql = switch (dialect) {
                case POSTGRES, SQLITE -> "INSERT INTO links(discord_id, mc_uuid, active, is_primary, created_at) VALUES(?,?,0,0,?) " +
                        "ON CONFLICT(discord_id, mc_uuid) DO UPDATE SET active=0";
                case MYSQL -> "INSERT INTO links(discord_id, mc_uuid, active, is_primary, created_at) VALUES(?,?,0,0,?) " +
                        "ON DUPLICATE KEY UPDATE active=VALUES(active)";
            };
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, discordId);
                ps.setString(2, profileUuid.toString());
                ps.setLong(3, now);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void activate(long discordId, UUID profileUuid) {
        long now = Instant.now().getEpochSecond();
        try (Connection c = ds.getConnection()) {
            // Deactivate other links for this profile
            try (PreparedStatement ps = c.prepareStatement("UPDATE links SET active=0 WHERE mc_uuid=? AND discord_id<>?")) {
                ps.setString(1, profileUuid.toString());
                ps.setLong(2, discordId);
                ps.executeUpdate();
            }

            // Check if user already has a primary link
            boolean hasPrimary = false;
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM links WHERE discord_id=? AND is_primary=1 LIMIT 1")) {
                ps.setLong(1, discordId);
                try (ResultSet rs = ps.executeQuery()) {
                    hasPrimary = rs.next();
                }
            }

            // Set is_primary=1 if this is the first active link for this discord_id
            int isPrimary = hasPrimary ? 0 : 1;

            // Upsert this pair as active=1
            String sql = switch (dialect) {
                case POSTGRES, SQLITE -> "INSERT INTO links(discord_id, mc_uuid, active, is_primary, created_at) VALUES(?,?,1,?,?) " +
                        "ON CONFLICT(discord_id, mc_uuid) DO UPDATE SET active=1, is_primary=EXCLUDED.is_primary";
                case MYSQL -> "INSERT INTO links(discord_id, mc_uuid, active, is_primary, created_at) VALUES(?,?,1,?,?) " +
                        "ON DUPLICATE KEY UPDATE active=VALUES(active), is_primary=VALUES(is_primary)";
            };
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, discordId);
                ps.setString(2, profileUuid.toString());
                ps.setInt(3, isPrimary);
                ps.setLong(4, now);
                ps.executeUpdate();
            }

            // Clear requires_reauth flag from discord_accounts after successful activation
            try (PreparedStatement ps = c.prepareStatement("UPDATE discord_accounts SET requires_reauth=0 WHERE discord_id=?")) {
                ps.setLong(1, discordId);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Long> linkedAt(UUID profileUUID) {
        String sql = "SELECT created_at FROM links WHERE mc_uuid = ? AND active = 1 LIMIT 1";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, profileUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                long v = rs.getLong("created_at");
                if (rs.wasNull()) return Optional.empty();

                return Optional.of(v);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private PreparedStatement upsertProfileSql(Connection c) throws Exception {
        return switch (dialect) {
            case POSTGRES, SQLITE -> c.prepareStatement(
                    "INSERT INTO mc_profiles(mc_uuid,name,updated_at) VALUES(?,?,?) " +
                            "ON CONFLICT(mc_uuid) DO UPDATE SET name=COALESCE(EXCLUDED.name, mc_profiles.name), updated_at=EXCLUDED.updated_at");
            case MYSQL -> c.prepareStatement(
                    "INSERT INTO mc_profiles(mc_uuid,name,updated_at) VALUES(?,?,?) " +
                            "ON DUPLICATE KEY UPDATE name=VALUES(name), updated_at=VALUES(updated_at)");
        };
    }

    private PreparedStatement upsertAccountSql(Connection c) throws Exception {
        return switch (dialect) {
            case POSTGRES, SQLITE -> c.prepareStatement(
                    "INSERT INTO discord_accounts(discord_id,username,global_name,email,avatar_hash,created_at,updated_at,profile_id) VALUES(?,?,?,?,?,?,?,?) " +
                            "ON CONFLICT(discord_id) DO UPDATE SET username=COALESCE(EXCLUDED.username, discord_accounts.username)," +
                            "global_name=COALESCE(EXCLUDED.global_name, discord_accounts.global_name)," +
                            "email=COALESCE(EXCLUDED.email, discord_accounts.email)," +
                            "avatar_hash=COALESCE(EXCLUDED.avatar_hash, discord_accounts.avatar_hash)," +
                            "updated_at=EXCLUDED.updated_at");
            case MYSQL -> c.prepareStatement(
                    "INSERT INTO discord_accounts(discord_id,username,global_name,email,avatar_hash,created_at,updated_at,profile_id) VALUES(?,?,?,?,?,?,?,?) " +
                            "ON DUPLICATE KEY UPDATE username=VALUES(username), global_name=VALUES(global_name), email=VALUES(email)," +
                            "avatar_hash=VALUES(avatar_hash), updated_at=VALUES(updated_at)");
        };
    }

    @Override
    public boolean isLinked(UUID profileUuid) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM links WHERE mc_uuid=? AND active=1 LIMIT 1")) {
            ps.setString(1, profileUuid.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    public Optional<Long> findDiscordForProfile(UUID profileUuid) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT discord_id FROM links WHERE mc_uuid=? AND active=1 LIMIT 1")) {
            ps.setString(1, profileUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(rs.getLong(1));
                return Optional.empty();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    public Optional<Long> findAnyDiscordForProfile(UUID profileUuid) {
        String sql = "SELECT discord_id FROM links WHERE mc_uuid=? ORDER BY active DESC LIMIT 1";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, profileUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(rs.getLong(1));
                return Optional.empty();
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    public Set<UUID> findProfilesForDiscord(long discordId) {
        Set<UUID> out = new HashSet<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT mc_uuid FROM links WHERE discord_id=? AND active=1")) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(UUID.fromString(rs.getString(1)));
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    @Override
    public int countByDiscordAndPlatform(long discordId, String platform, boolean includeReserved) {
        String sql = "SELECT COUNT(1) FROM links l JOIN mc_profiles p ON p.mc_uuid = l.mc_uuid " +
                "WHERE l.discord_id=? " +
                (includeReserved ? "" : "AND l.active=1 ") +
                "AND UPPER(p.platform)=UPPER(?)";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setString(2, platform);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isPrimaryLink(long discordId, UUID profileUuid) {
        String sql = "SELECT is_primary FROM links WHERE discord_id=? AND mc_uuid=? AND active=1";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setString(2, profileUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("is_primary") == 1;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    @Override
    public Optional<UUID> findPrimaryProfileForDiscord(long discordId) {
        String sql = "SELECT mc_uuid FROM links WHERE discord_id=? AND active=1 AND is_primary=1 LIMIT 1";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(UUID.fromString(rs.getString("mc_uuid")));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    @Override
    public void setPrimaryLink(long discordId, UUID profileUuid) {
        try (Connection c = ds.getConnection()) {
            // Unset all other primary flags for this discord_id
            try (PreparedStatement ps = c.prepareStatement("UPDATE links SET is_primary=0 WHERE discord_id=?")) {
                ps.setLong(1, discordId);
                ps.executeUpdate();
            }
            // Set this link as primary
            try (PreparedStatement ps = c.prepareStatement("UPDATE links SET is_primary=1 WHERE discord_id=? AND mc_uuid=? AND active=1")) {
                ps.setLong(1, discordId);
                ps.setString(2, profileUuid.toString());
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new IllegalStateException("Cannot set primary on non-existent or inactive link");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<UUID, Boolean> findLinksWithPrimaryFlag(long discordId) {
        Map<UUID, Boolean> result = new HashMap<>();
        String sql = "SELECT mc_uuid, is_primary FROM links WHERE discord_id=? AND active=1";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("mc_uuid"));
                    boolean isPrimary = rs.getInt("is_primary") == 1;
                    result.put(uuid, isPrimary);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    @Override
    public boolean requiresReauth(long discordId) {
        String sql = "SELECT requires_reauth FROM discord_accounts WHERE discord_id=? LIMIT 1";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) == 1;
                return false;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int markAllForReauth() {
        String sql = "UPDATE discord_accounts SET requires_reauth=1";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            return ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void markForReauth(long discordId) {
        String sql = "UPDATE discord_accounts SET requires_reauth=1 WHERE discord_id=?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void clearReauth(long discordId) {
        String sql = "UPDATE discord_accounts SET requires_reauth=0 WHERE discord_id=?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
