CREATE TABLE IF NOT EXISTS discord_accounts (
  discord_id         BIGINT PRIMARY KEY,
  username           VARCHAR(255),
  global_name        VARCHAR(255),
  email              VARCHAR(320),
  avatar_hash        VARCHAR(255),
  commands_installed TINYINT(1) NOT NULL DEFAULT 0,
  updated_at         BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS oauth_tokens (
  discord_id   BIGINT PRIMARY KEY,
  access_enc   BLOB NOT NULL,
  refresh_enc  BLOB NOT NULL,
  token_type   VARCHAR(32) NOT NULL,
  scope        TEXT NOT NULL,
  expires_at   BIGINT NOT NULL,
  updated_at   BIGINT NOT NULL,
  CONSTRAINT fk_tokens_user FOREIGN KEY (discord_id)
    REFERENCES discord_accounts(discord_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mc_profiles (
  mc_uuid     VARCHAR(36) PRIMARY KEY,
  name        VARCHAR(64),
  last_ip     VARCHAR(45),
  platform    VARCHAR(16),
  updated_at  BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS links (
  discord_id  BIGINT NOT NULL,
  mc_uuid     VARCHAR(36) NOT NULL,
  active      TINYINT(1) NOT NULL DEFAULT 1,
  created_at  BIGINT NOT NULL,
  PRIMARY KEY (discord_id, mc_uuid),
  CONSTRAINT fk_links_user FOREIGN KEY (discord_id) REFERENCES discord_accounts(discord_id) ON DELETE CASCADE,
  CONSTRAINT fk_links_profile FOREIGN KEY (mc_uuid)  REFERENCES mc_profiles(mc_uuid)      ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_links_discord ON links(discord_id);
CREATE INDEX idx_links_mc      ON links(mc_uuid);
