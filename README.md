# NerdBot

A completely over-engineered solution to automating features in the SkyBlock Nerds Discord server.

## Features

### Greenlighting Suggestions

The bot will automatically track and greenlight suggestions that reach a specified ratio.

### Channel Groups

A ChannelGroup is two channels that are linked together to submit and collect approved suggestions.

### Reaction Tracking

The bot will automatically track reactions from users on suggestions and store them for later use.

## Config

An example config file can be
found [here](https://github.com/TheMGRF/NerdBot/blob/master/src/main/resources/example-config.json).

Depending on the current environment, the configuration file will either be named `production.config.json`
or `dev.config.json`

Changes to the configuration file require the bot to be restarted.

## Commands

The default bot prefix is `!` and can be changed in the config file.

| Command                               | Description                                                             | Permission Required |
|---------------------------------------|-------------------------------------------------------------------------|---------------------|
| `!curate`                             | Make the bot start a manual curation process on suggestions             | BAN_MEMBERS         |
| `!userstats <user>`                   | Display some stats about a user such as their agree and disagree amount | BAN_MEMBERS         |
| `!addchannelgroup <name> <from> <to>` | Add a channel group to collect and send suggestions                     | BAN_MEMBERS         |
| `!removechannelgroup <name>`          | Remove a channel group                                                  | BAN_MEMBERS         |
| `!getchannelgroups`                   | Display all channel groups                                              | BAN_MEMBERS         |
| `!uptime`                             | Display the current bot uptime                                          | BAN_MEMBERS         |
| `!botinfo`                            | Show more detailed information on the bot status                        | BAN_MEMBERS         |

## Running the Bot

Requirements:

- A [MongoDB database](https://www.mongodb.com/free-cloud-database) instance
- A valid [Discord bot](https://discord.com/developers/applications/me) token

To start the bot, run the following command:

```shell
$ java -Dmongodb.uri="YOUR_DATABASE_CONNECTION_STRING" -Dbot.token="YOUR_DISCORD_BOT_TOKEN" -Dbot.region="REGION" [-options] -jar NerdBot.jar
```