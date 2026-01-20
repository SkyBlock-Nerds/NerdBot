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

## Mod Mail

Users can send messages to the server's management team by sending a DM to the bot. The bot will then forward the
message to the staff team, and they can respond to the user by replying to the bot's message in the appropriate thread.

## Mod Logs

The bot tracks and logs a number of events that occur in the server. These events include message edits, message
deletes, user joins, user leaves, and more.

## Reaction Channels

These are channels that are defined in the config file that the bot will monitor for new messages and add reactions to
them.

## Metrics

The bot tracks a number of custom metrics that are implemented using Prometheus. These metrics can then be viewed using
a dashboard of your choice that supports Prometheus.

# Running the bot

Please follow the instructions [here](https://github.com/SkyBlock-Nerds/NerdBot/blob/master/CONTRIBUTING.md)

# Commands

See the [commands](https://github.com/SkyBlock-Nerds/NerdBot/tree/master/src/main/java/net/hypixel/nerdbot/command)
package.

