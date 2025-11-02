package ua.beengoo.logdo2.plugin.adapters.jdbc;

import ua.beengoo.logdo2.api.ports.DiscordUserRepo;
import ua.beengoo.logdo2.plugin.db.DatabaseManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Optional;

public class JdbcDiscordUserRepo implements DiscordUserRepo {
    private final DataSource ds;
    private final DatabaseManager.Dialect dialect;

    public JdbcDiscordUserRepo(DataSource ds, DatabaseManager.Dialect dialect) {
        this.ds = ds; this.dialect = dialect;
    }

    @Override
    public Optional<String> findEmailByDiscordId(long discordId) {
        String sql = "SELECT email FROM discord_accounts WHERE discord_id=? LIMIT 1";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    String email = rs.getString("email");
                    if (email != null && !email.isBlank()) {
                        return Optional.of(email);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    @Override
    public void upsertUser(long discordId, String username, String globalName, String email, String avatarHash) {
        long now = Instant.now().getEpochSecond();
        String sql = switch (dialect) {
            case POSTGRES, SQLITE -> """
                 INSERT INTO discord_accounts(discord_id, username, global_name, email, avatar_hash, updated_at)
                 VALUES(?,?,?,?,?,?)
                 ON CONFLICT(discord_id) DO UPDATE SET
                  username=EXCLUDED.username, global_name=EXCLUDED.global_name,
                  email=EXCLUDED.email, avatar_hash=EXCLUDED.avatar_hash, updated_at=EXCLUDED.updated_at
                 """;
            case MYSQL -> """
                 INSERT INTO discord_accounts(discord_id, username, global_name, email, avatar_hash, updated_at)
                 VALUES(?,?,?,?,?,?)
                 ON DUPLICATE KEY UPDATE
                  username=VALUES(username), global_name=VALUES(global_name),
                  email=VALUES(email), avatar_hash=VALUES(avatar_hash), updated_at=VALUES(updated_at)
                 """;
        };
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setString(2, username);
            ps.setString(3, globalName);
            ps.setString(4, email);
            ps.setString(5, avatarHash);
            ps.setLong(6, now);
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    public void setCommandsInstalled(long discordId, boolean installed) {
        String sql = "UPDATE discord_accounts SET commands_installed=?, updated_at=? WHERE discord_id=?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, installed ? 1 : 0);
            ps.setLong(2, Instant.now().getEpochSecond());
            ps.setLong(3, discordId);
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
