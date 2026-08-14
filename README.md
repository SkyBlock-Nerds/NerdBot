<div align="center">
    <h1>Nerd Bot</h1>
    <img src="https://img.shields.io/github/issues/SkyBlock-Nerds/NerdBot?style=for-the-badge" alt="Issues"/>
    <img src="https://img.shields.io/github/issues-pr/SkyBlock-Nerds/NerdBot?style=for-the-badge" alt="Pull Requests"/>
    <img src="https://img.shields.io/github/last-commit/SkyBlock-Nerds/NerdBot?style=for-the-badge" alt="Last Commit"/>
    <img src="https://img.shields.io/github/contributors/SkyBlock-Nerds/NerdBot?style=for-the-badge" alt="Contributors"/>
</div>

---

## Supporting the Project

Nerd Bot is a passion project built for the SkyBlock Nerds Discord server. Keeping it running involves server hosting
costs, and maintaining and improving the bot takes time and effort.
If you've found the Nerd Bot's features useful in any way, all contributions are greatly appreciated and are put towards 
infrastructure costs.

<div align="center">
    <a href="https://github.com/sponsors/Aerhhh"><img src="https://img.shields.io/static/v1?label=Sponsor&message=%E2%9D%A4&logo=GitHub&color=%23fe8e86" height="20px" alt="Aerh's GitHub Sponsor Profile"></a>
    <a href="https://www.buymeacoffee.com/aaerh"><img src="https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png" height="20px" alt="Aerh's Buy Me A Coffee Profile"></a>
    <a href="https://ko-fi.com/A0A81MQI3"><img src="https://ko-fi.com/img/githubbutton_sm.svg" height="20px" alt="Aerh's Ko-Fi Profile"></a>
</div>

---

# Features

## Activity Tracking

The bot tracks the activity of all users in the server and saves the data to a database. There are a number of commands
that will display this information in a human-readable format.

## Reminders

Users can create one-time reminders that are sent privately via Discord's Direct Messages.

## Endpoint Polling

The bot will periodically poll the Hypixel API and the Hypixel Forums for the latest SkyBlock news, updates, Fire Sales,
and more.

When a new update is found, the bot will send a message in the assigned channel and ping a role defined in code. This
role is defined in the config file.

## Suggestion Curation

The bot will periodically review defined suggestions channels and determine if a suggestion has enough votes to be
considered to be greenlit. Values used to determine if a suggestion is greenlit are defined in the config file.

If a suggestion is greenlit, the bot will assign the 'Greenlit' tag to the suggestion which can be used for filtering.

## User Verification

Users are able to link their Minecraft account to their Discord account by using the `/verify` command. This command
checks the Hypixel API to ensure that the user's Discord account is linked to the specified Minecraft account through
the Hypixel Network's in-game social menu.

## Image Generation

The bot can generate images of Minecraft items, tooltips, and crafting recipes with user-provided data. It also supports
parsing NBT data into an image and returning an editable command.

## Tickets

Users can create tickets to contact the server's management team. Tickets are created as private channels where
users and staff can communicate directly. Staff can claim, transfer, and manage ticket statuses through an interactive
control panel.

## Mod Logs

The bot tracks and logs a number of events that occur in the server. These events include message edits, message
deletes, user joins, user leaves, and more.

## Reaction Channels

These are channels that are defined in the config file that the bot will monitor for new messages and add reactions to
them.

## Metrics

The bot tracks a number of custom metrics that are implemented using Prometheus. These metrics can then be viewed using
a dashboard of your choice that supports Prometheus.

## Google Drive Access Sync

Members link their Google account email with `/drive link` (a modal, so the address never appears in chat, and every
reply is ephemeral), and the bot keeps their shared-Drive folder access in sync with their Discord roles: holding a
mapped role grants access at that role's level, holding several mapped roles gets the union of every folder they
unlock (the most permissive level wins where two roles map to the same folder), and losing a role, leaving, being
kicked, or being banned revokes every grant and deletes the stored email. Role changes sync instantly through
Discord's events, and an hourly reconcile sweep re-checks every linked member and heals anything that slipped
through, including permissions removed by hand in Drive. Linked emails are encrypted at rest with AES-256-GCM, and
every grant and revoke is logged.

# Running the bot

Please follow the instructions [here](./CONTRIBUTING.md)

# Commands

See the [commands](./app/src/main/java/net/hypixel/nerdbot/app/command) package.

# Google Drive Access Sync Setup

This section covers turning the feature on. It's fully inert until you complete it: `googleDriveConfig.enabled` is
`false` by default in the config, and even with it set to `true` the bot needs both secrets below before it will do
anything.

## Secrets

Two JVM system properties, passed the same way as `bot.token`:

- `drive.credentials.path`: path to the Google service account's JSON key file.
- `drive.email.key`: a base64-encoded 32-byte AES key used to encrypt linked emails at rest. Generate one with:

  ```bash
  openssl rand -base64 32
  ```

**Pick this key before the first member links.** There's no rotation path. If you change it later, every
previously stored email becomes undecryptable and those members will need to relink.

## One-time Google setup

1. Create (or reuse) a GCP project.
2. Enable the Google Drive API for that project.
3. Create a service account in the project, using the "Application data" credential type.
4. Download the service account's JSON key file.
5. In the shared drive that holds the folders you want to sync, have a Manager add the service account's
   `client_email` (from the JSON key) as a **Manager** on the shared drive itself, not on individual folders.
   Drive-level membership covers every subfolder, including ones you add to `folderMappings` later.

## Configuration

The `googleDriveConfig` top-level block in the config file controls the feature (see
[`example-config.json`](./example-config.json)):

```json
"googleDriveConfig": {
  "enabled": false,
  "folderMappings": [
    {
      "roleId": "1234567890123456789",
      "folderIds": ["example_folder_id_1", "example_folder_id_2"],
      "accessLevel": "READER"
    }
  ]
}
```

- `enabled`: turns the subsystem on. Even when `true`, it stays inert until both secrets above are set. The Drive
  service is built once at startup, so enabling it requires a full bot restart; `/config reload` alone will not
  activate it.
- `folderMappings`: one entry per Discord role: the shared-Drive folders that role grants access to, and the level
  it grants. `accessLevel` is one of `READER`, `COMMENTER`, or `WRITER`.
- `sendNotificationEmails`: whether members receive Google's share-notification email when the bot grants them
  folder access (default `true`). Revokes never generate an email; that's a Drive limitation.
- `notificationEmailMessage`: custom text Google includes in that email, so it explains itself despite the raw
  service-account sender address. Blank uses Google's default wording.
- A member holding several mapped roles gets the union of every folder those roles unlock; where two roles map to
  the same folder at different levels, the more permissive level wins.

## Docker deployment

Production deploys run through the [Publish workflow](.github/workflows/publish.yml): set the `DRIVE_CREDENTIALS_FOLDER_PATH`
and `DRIVE_EMAIL_KEY` repo secrets, drop `drive-credentials.json` (the service account key, renamed, `chmod 600`) into
the configured folder on the vps, and the workflow mounts it and passes both `-Ddrive.*` flags automatically. Both
secrets are optional; leave them unset and the feature stays off. See the secrets list at the top of the workflow
file for details.

The `docker run`/`docker-compose` snippets below are a manual/local reference, not what production uses. The host
directory is up to you; these examples use `/opt/nerdbot/` (ours), holding `production.config.json` and
`drive-credentials.json`. No Dockerfile change is needed; the entrypoint already expands `JAVA_OPTS`.

`docker run`:

```bash
docker run \
  -v /opt/nerdbot/production.config.json:/app/production.config.json \
  -v /opt/nerdbot/drive-credentials.json:/app/secrets/drive-credentials.json:ro \
  -e JAVA_OPTS="-Dbot.token=<your-bot-token> -Ddb.mongodb.uri=<your-mongodb-uri> ... -Ddrive.credentials.path=/app/secrets/drive-credentials.json -Ddrive.email.key=<your-base64-key>" \
  nerd-bot
```

(`...` stands in for whatever other flags you already pass in `JAVA_OPTS`, just append the two Drive ones.)

Equivalent `docker-compose`:

```yaml
services:
  nerdbot:
    volumes:
      - /opt/nerdbot/production.config.json:/app/production.config.json
      - /opt/nerdbot/drive-credentials.json:/app/secrets/drive-credentials.json:ro
    environment:
      JAVA_OPTS: >-
        -Dbot.token=<your-bot-token> -Ddb.mongodb.uri=<your-mongodb-uri> ...
        -Ddrive.credentials.path=/app/secrets/drive-credentials.json
        -Ddrive.email.key=<your-base64-key>
```
