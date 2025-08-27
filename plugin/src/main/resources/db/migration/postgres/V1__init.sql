CREATE TABLE IF NOT EXISTS discord_accounts (
  discord_id         BIGINT PRIMARY KEY,
  username           TEXT,
  global_name        TEXT,
  email              TEXT,
  avatar_hash        TEXT,
  commands_installed INTEGER NOT NULL DEFAULT 0,
  updated_at         BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS oauth_tokens (
  discord_id   BIGINT PRIMARY KEY,
  access_enc   BYTEA NOT NULL,
  refresh_enc  BYTEA NOT NULL,
  token_type   TEXT NOT NULL,
  scope        TEXT NOT NULL,
  expires_at   BIGINT NOT NULL,
  updated_at   BIGINT NOT NULL,
  CONSTRAINT fk_tokens_user FOREIGN KEY (discord_id)
    REFERENCES discord_accounts(discord_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS mc_profiles (
  mc_uuid     TEXT PRIMARY KEY,
  name        TEXT,
  last_ip     TEXT,
  platform    TEXT,
  updated_at  BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS links (
  discord_id  BIGINT NOT NULL,
  mc_uuid     TEXT   NOT NULL,
  active      INTEGER NOT NULL DEFAULT 1,
  created_at  BIGINT  NOT NULL,
  PRIMARY KEY (discord_id, mc_uuid),
  CONSTRAINT fk_links_user   FOREIGN KEY (discord_id) REFERENCES discord_accounts(discord_id) ON DELETE CASCADE,
  CONSTRAINT fk_links_profile FOREIGN KEY (mc_uuid)    REFERENCES mc_profiles(mc_uuid)      ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_links_discord ON links(discord_id);
CREATE INDEX IF NOT EXISTS idx_links_mc      ON links(mc_uuid);
