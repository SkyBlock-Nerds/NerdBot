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

### For the most basic config to be valid it will need:

- Emoji's all set (Agree, disagree, neutral, and greenlit)
- Log channel ID set
- Guild ID set
- Activity string set

### Additionally, other things may be required to be set in the config before those features will work.

- Suggestion forum id for suggestion parsing.
- Item gen channel(s) for allowing itemgen to work in them.
- Tag ID set for greenlit to work.

### Other notes:

Depending on the current environment, the configuration file will either be named `production.config.json`
or `dev.config.json`

Changes to the configuration file require the bot to be restarted.

