package ua.beengoo.logdo2.plugin.adapters.jdbc;

import ua.beengoo.logdo2.api.ports.TokensRepo;
import ua.beengoo.logdo2.plugin.db.DatabaseManager;
import ua.beengoo.logdo2.plugin.util.TokenCrypto;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;

public class JdbcTokensRepo implements TokensRepo {
    private final DataSource ds;
    private final TokenCrypto crypto;
    private final DatabaseManager.Dialect dialect;

    public JdbcTokensRepo(DataSource ds, TokenCrypto crypto, DatabaseManager.Dialect dialect) {
        this.ds = ds; this.crypto = crypto; this.dialect = dialect;
    }

    @Override
    public void save(long discordId, String accessToken, String refreshToken, Instant expiresAt,
                     String tokenType, String scope) {
        long now = Instant.now().getEpochSecond();
        byte[] access = crypto.encrypt(accessToken.getBytes(StandardCharsets.UTF_8));
        byte[] refresh = crypto.encrypt(refreshToken.getBytes(StandardCharsets.UTF_8));

        String sql = switch (dialect) {
            case POSTGRES, SQLITE -> """
                INSERT INTO oauth_tokens(discord_id, access_enc, refresh_enc, token_type, scope, expires_at, updated_at)
                VALUES(?,?,?,?,?,?,?)
                ON CONFLICT(discord_id) DO UPDATE SET
                  access_enc=EXCLUDED.access_enc, refresh_enc=EXCLUDED.refresh_enc,
                  token_type=EXCLUDED.token_type, scope=EXCLUDED.scope,
                  expires_at=EXCLUDED.expires_at, updated_at=EXCLUDED.updated_at
            """;
            case MYSQL -> """
                INSERT INTO oauth_tokens(discord_id, access_enc, refresh_enc, token_type, scope, expires_at, updated_at)
                VALUES(?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                  access_enc=VALUES(access_enc), refresh_enc=VALUES(refresh_enc),
                  token_type=VALUES(token_type), scope=VALUES(scope),
                  expires_at=VALUES(expires_at), updated_at=VALUES(updated_at)
            """;
        };

        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setBytes(2, access);
            ps.setBytes(3, refresh);
            ps.setString(4, tokenType);
            ps.setString(5, scope);
            ps.setLong(6, expiresAt.getEpochSecond());
            ps.setLong(7, now);
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    public Optional<TokenView> find(long discordId) {
        String sql = "SELECT access_enc, refresh_enc, token_type, scope, expires_at FROM oauth_tokens WHERE discord_id=?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                String access = new String(crypto.decrypt(rs.getBytes(1)), StandardCharsets.UTF_8);
                String refresh= new String(crypto.decrypt(rs.getBytes(2)), StandardCharsets.UTF_8);
                String ttype  = rs.getString(3);
                String scope  = rs.getString(4);
                Instant exp   = Instant.ofEpochSecond(rs.getLong(5));
                return Optional.of(new TokenView(access, refresh, exp, ttype, scope));
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
