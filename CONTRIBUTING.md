# Running the Bot

## Requirements:

- A [MongoDB database](https://www.mongodb.com/free-cloud-database) instance
- A valid [Discord bot](https://discord.com/developers/applications/me) token
- A valid config file, see the below topic for more details.

To start the bot, run the following command:

```shell
$ java -Dbot.token="DISCORD_BOT_TOKEN" -Dmongodb.uri="MONGODB_URI" -Dbot.environment="ENVIRONMENT" -Dbot.config="INSERT_CONFIG_PATH_HERE" -jar NerdBot.jar
```


## Config

An example config file can be
found [here](https://github.com/TheMGRF/NerdBot/blob/master/src/main/resources/example-config.json).

 - This file is automatically updated when values are added, removed, or changed.

### For the most basic config to be valid it will need:

- Emojis:
  - Agree
  - Disagree
  - Neutral
  - Greenlit
- Log channel ID set
- Guild ID set
- Activity string set

### Additionally, other things may be required to be set in the config before those features will work.

- Channel ID of a suggestions channel in order to curate suggestions
- Channel ID('s) of item generator channel(s) to allow the item generation commands to be used

### Other notes:

Depending on the current environment, the configuration file will either be named `production.config.json` or `dev.config.json`

Changes to the configuration file can be reloaded by doing `/config reload`

