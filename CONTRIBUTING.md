# Running the Bot

## Requirements:

- A [MongoDB database](https://www.mongodb.com/free-cloud-database) instance
- A valid [Discord bot](https://discord.com/developers/applications/me) token
- A valid [config file](https://github.com/SkyBlock-Nerds/NerdBot/blob/master/CONTRIBUTING.md#config)
- Optional: [Docker](https://www.docker.com/)

## With Docker

### Environment Variables/Arguments

- `GITHUB_USERNAME` - Your GitHub username
- `GITHUB_TOKEN` - Your GitHub token
- `REPO_USERNAME` - The username of the repository owner (default: SkyBlock-Nerds)
- `REPO_NAME` - The name of the repository (default: NerdBot)
- `REPO_BRANCH` - The branch of the repository to clone (default: master)
- `SOURCE_CODE_DIR` - The directory to clone the repository into (default: repository)
- `JAR_FILE_NAME` - The name of the jar file to build (default: NerdBot.jar)

A Dockerfile is provided to build the bot into a Docker image. To build the image, run the following command:

`docker build -t nerd-bot --build-arg GITHUB_USERNAME=<your-github-username> --build-arg GITHUB_TOKEN=<your-github-token> <...other arguments> .`

Replace `<your-github-username>` with your GitHub username and `<your-github-token>` with your GitHub token. If you do
not specify a GitHub username or GitHub token then it will default to cloning the repository with no credentials. You
can also replace `<...other arguments>` with any of the other arguments listed above.

Every time this image is built, it will automatically pull the latest changes from the repository and compile
application.

---

To run the bot, you need to include the config file in the same directory as the main application file, and if you want
metrics to work then you'll need to expose a port for the Prometheus server. To do this, run the following command:

`docker run -p 1234:1234 -v /path/to/*.config.json:/app/*.config.json -e JAVA_OPTS="-Dbot.token=<your-bot-token> -Ddb.mongodb.uri=<your-mongodb-credentials> ..." nerd-bot`

Replace `<your-bot-token>` with your Discord bot token and `<your-mongodb-credentials>` with your MongoDB credentials.
Other arguments can be added to the `JAVA_OPTS` environment variable depending on your needs. You should also
replace `/path/to/*.config.json` with the path to your config file. If you are using a config file
named `production.config.json`, you can replace `*.config.json` with `production.config.json` and vice versa.

## Without Docker

To start the bot, run the following command:

```shell
$ java -Dbot.token="DISCORD_BOT_TOKEN" -Ddb.mongodb.uri="MONGODB_URI" -Dbot.environment="ENVIRONMENT" -Dbot.config="INSERT_CONFIG_PATH_HERE" -jar NerdBot.jar
```

## Configuration File Template

An example config file can be
found [here](https://github.com/SkyBlock-Nerds/NerdBot/blob/master/src/main/resources/example-config.json).

This file is automatically updated when values are added, removed, or changed.

### Basic Configuration Requirements:

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

### Notes:

- Depending on the current environment, the configuration file will either be named `production.config.json`
  or `dev.config.json`

- Changes to the configuration file can be reloaded by doing `/config reload`

