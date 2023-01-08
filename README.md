# NerdBot

A completely over-engineered solution to automating features in the SkyBlock Nerds Discord server.

## Features

### Greenlighting Suggestions

The bot will automatically track and greenlight suggestions that reach a specified ratio.

### Channel Groups

A ChannelGroup is two channels that are linked together to submit and collect approved suggestions.

### Mod Mail

A Mod Mail system where users can DM the bot user, have the requests sent into a Forum Channel, and responses received
in the same DM.

### Mod Logs

The bot listens to a number of events and logs them in a channel.

## Config

An example config file can be
found [here](https://github.com/TheMGRF/NerdBot/blob/master/src/main/resources/example-config.json).

Depending on the current environment, the configuration file will either be named `production.config.json`
or `dev.config.json`

Changes to the configuration file require the bot to be restarted.

## Commands

See the [commands](https://github.com/TheMGRF/NerdBot/tree/master/src/main/java/net/hypixel/nerdbot/command) package.

## Running the Bot

Requirements:

- A [MongoDB database](https://www.mongodb.com/free-cloud-database) instance
- A valid [Discord bot](https://discord.com/developers/applications/me) token

To start the bot, run the following command:

```shell
$ java -Dmongodb.uri="YOUR_DATABASE_CONNECTION_STRING" -Dbot.token="YOUR_DISCORD_BOT_TOKEN" -Dbot.region="REGION" [-options] -jar NerdBot.jar
```
