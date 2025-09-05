# LogDO2
### Authorize your Minecraft profile with just \*one click\*!

LogDO2 - Is simple Discord OAuth2 login implementation for Minecraft servers.
## Features
- Discord OAuth2 linking: Chat link for Java; one‑time code + DM flow for Bedrock.
- IP confirmation: Owner receives a Discord DM to confirm/reject logins from new IPs.
- Floodgate support: Automatically detects Bedrock and guides them through the code flow.
- Progressive bans: Auto‑increasing temporary bans on rejected/abusive attempts.
- Paper & Folia compatible: Uses Folia-safe scheduling where available.
- Multi‑DB support: SQLite (default), MySQL, PostgreSQL with auto migrations.
- Admin tools: Commands for linking/unlinking, forgiving IP bans, and quick reload.

## Configuration (`config.yml`)
- `web.port`: HTTP port for the embedded server (default `8080`).
- `web.publicUrl`: Base public URL used to build login links and redirect URIs.
- `discord.botToken`: Your bot token (required). `targetGuildId`/`inviteChannelId` for post-login invites.
- `oauth.clientId` / `oauth.clientSecret` / `oauth.scopes`: Discord OAuth2 credentials and scopes.
- `database`: JDBC configuration; examples for SQLite/MySQL/Postgres are included in the file.
- `security.tokenEncryptionKeyBase64`: Base64-encoded 32-byte key to encrypt access/refresh tokens.
- `timeouts`: Time limits for login and IP confirmation flows.
- `bans`: Progressive ban settings (base/multiplier/max/tracking window and reason template).
- `limits.perDiscord`: Caps per Discord account (separate for Java/Bedrock), and simultaneous-play prevention.
- `postLogin`: What the browser shows after OAuth (`text`, `discord-invite`, or `redirect`).
- `gates.login` and `gates.ipConfirm`: What actions are allowed during each phase (movement, chat, commands, etc.).
- `audit`: Persist LogDO2 actions to a separated log file.

Messages are in `messages.yml`.

## Security
Sensitive tokens are stored encrypted (AES). Generate your own 32‑byte key and set `security.tokenEncryptionKeyBase64`.

Example (Linux/macOS):
`openssl rand -base64 32`

Keep this key secret. Changing it will invalidate stored tokens.

## Player Flows
- Java edition:
  - Player joins → receives a clickable chat link → signs in with Discord → returns to game.
- Bedrock via Floodgate:
  - Player joins → receives a one‑time code → sends `/login <code>` to the bot in DM → receives OAuth link → completes in browser.
- IP change confirmation:
  - On new IP, the account owner gets a Discord DM with Confirm/Reject buttons. Rejects can trigger progressive bans.

## Commands & Permissions
- `/logdo2 help`: Show help.
- `/logdo2 link <player_uuid> <discord_id>`: Reserve/link a player for a Discord user (OAuth still required).
- `/logdo2 logout <player|uuid|discord_id>`: Unlink target; kicks online players linked to that Discord.
- `/logdo2 logout <discord_id> <player|uuid>`: Unlink only that mapping.
- `/logdo2 forgive <ip>`: Reset progressive ban tracking for the IP.
- `/logdo2 bypass <player|uuid>`: Allow profile to ignore per-Discord limit (OAuth still required)
- `/logdo2 reload`: Reload config and messages.

Permission: `logdo2.admin` (children: `logdo2.admin.link`, `logout`, `forgive`, `bypass`, `reload`).

## API
Currently not tested at any projects, but exists!

## Databases
- Default: SQLite at `plugins/LogDO2/logdo2.db`.
- MySQL/PostgreSQL supported via `database.url` and `driver` keys.
- Tables are created/updated at startup using SQL migrations bundled in the plugin.

## Building
- Clone repo
- Run `./gradlew build` in terminal
- Get your own plugin build in `plugin/build/libs/`
- Make your self happy about it!

## Compatibility
- Paper and Folia are supported. Spigot may lack required APIs and is not officially supported.

## FAQ
Q: Why Java and Bedrock login flows are different?

A: Unfortunately, Bedrock players do not have clickable links in the chat, which makes it impossible to use the same login flow as on java

Q: What SUNSHINE and MOONLIGHT builds means?

A: These are builds where we add new functionality that is not yet tested to be included in stable releases. SUNSHINE and MOONLIGHT simply indicate when the version was released during the day or at night.

## License
MIT License. See `LICENSE` for details.
