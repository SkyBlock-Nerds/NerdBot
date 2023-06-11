package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.google.gson.*;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.InviteAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.bot.Bot;
import net.hypixel.nerdbot.api.curator.Curator;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.curator.ForumChannelCurator;
import net.hypixel.nerdbot.util.Environment;
import net.hypixel.nerdbot.util.JsonUtil;
import net.hypixel.nerdbot.util.Util;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Log4j2
public class AdminCommands extends ApplicationCommand {

    private static final String REGEX = "^[a-zA-Z0-9_]{2,16}";
    private static final String SURROUND_REGEX = "\\|([^|]+)\\||\\[([^\\[]+)\\]|\\{([^\\{]+)\\}|\\(([^\\(]+)\\)";

    private final Queue<String> usernameQueue = new LinkedList<>();

    @JDASlashCommand(name = "curate", description = "Manually run the curation process", defaultLocked = true)
    public void curate(GuildSlashEvent event, @AppOption ForumChannel channel, @Optional @AppOption(description = "Run the curator without greenlighting suggestions") Boolean readOnly) {
        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            log.error("Couldn't connect to the database!");
            return;
        }

        if (readOnly == null) {
            readOnly = false;
        }

        Curator<ForumChannel> forumChannelCurator = new ForumChannelCurator(readOnly);
        NerdBotApp.EXECUTOR_SERVICE.execute(() -> {
            event.deferReply(true).queue();
            List<GreenlitMessage> output = forumChannelCurator.curate(channel);
            if (output.isEmpty()) {
                event.getHook().editOriginal("No suggestions were greenlit!").queue();
            } else {
                event.getHook().editOriginal("Greenlit " + output.size() + " suggestion(s) in " + (forumChannelCurator.getEndTime() - forumChannelCurator.getStartTime()) + "ms!").queue();
            }
        });
    }

    @JDASlashCommand(name = "invites", subcommand = "create", description = "Generate a bunch of invites for a specific channel", defaultLocked = true)
    public void createInvites(GuildSlashEvent event, @AppOption int amount, @AppOption TextChannel channel) {
        List<Invite> invites = new ArrayList<>(amount);

        event.deferReply(true).queue();

        if (ChannelManager.getLogChannel() != null) {
            ChannelManager.getLogChannel().sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("Invites generated")
                    .setDescription("Generating " + amount + " invite(s) for " + channel.getAsMention() + " by " + event.getUser().getAsMention())
                    .build()
            ).queue();
        }

        for (int i = 0; i < amount; i++) {
            InviteAction action = channel.createInvite()
                    .setUnique(true)
                    .setMaxAge(7L, TimeUnit.DAYS)
                    .setMaxUses(1);

            Invite invite = action.complete();
            invites.add(invite);
            log.info("Generated new temporary invite '" + invite.getUrl() + "' for channel " + channel.getName() + " by " + event.getUser().getAsTag());
        }

        StringBuilder stringBuilder = new StringBuilder("Generated invites (");
        stringBuilder.append(invites.size()).append("):\n");

        invites.forEach(invite -> stringBuilder.append(invite.getUrl()).append("\n"));
        event.getHook().editOriginal(stringBuilder.toString()).queue();
    }

    @JDASlashCommand(name = "invites", subcommand = "delete", description = "Delete all active invites", defaultLocked = true)
    public void deleteInvites(GuildSlashEvent event) {
        event.deferReply(true).queue();

        List<Invite> invites = event.getGuild().retrieveInvites().complete();
        invites.forEach(invite -> {
            invite.delete().complete();
            log.info(event.getUser().getAsTag() + " deleted invite " + invite.getUrl());
        });

        if (ChannelManager.getLogChannel() != null) {
            ChannelManager.getLogChannel().sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("Invites deleted")
                    .setDescription("Deleted " + invites.size() + " invite(s) by " + event.getUser().getAsMention())
                    .build()
            ).queue();
        }
        event.getHook().editOriginal("Deleted " + invites.size() + " invites").queue();
    }

    @JDASlashCommand(name = "config", subcommand = "show", description = "View the currently loaded config", defaultLocked = true)
    public void showConfig(GuildSlashEvent event) {
        Gson jsonConfig = new GsonBuilder().setPrettyPrinting().create();
        event.reply("```json\n" + jsonConfig.toJson(NerdBotApp.getBot().getConfig()) + "```").setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "config", subcommand = "reload", description = "Reload the config file", defaultLocked = true)
    public void reloadConfig(GuildSlashEvent event) {
        Bot bot = NerdBotApp.getBot();
        bot.loadConfig();
        bot.getJDA().getPresence().setActivity(Activity.of(bot.getConfig().getActivityType(), bot.getConfig().getActivity()));
        event.reply("Reloaded the config file!").setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "config", subcommand = "edit", description = "Edit the config file", defaultLocked = true)
    public void editConfig(GuildSlashEvent event, @AppOption String key, @AppOption String value) {
        // We should store the name of the config file on boot lol this is bad
        String fileName = System.getProperty("bot.config") != null ? System.getProperty("bot.config") : Environment.getEnvironment().name().toLowerCase() + ".config.json";
        JsonObject obj = JsonUtil.readJsonFile(fileName);
        if (obj == null) {
            event.reply("An error occurred when reading the JSON file, please try again later!").setEphemeral(true).queue();
            return;
        }

        JsonElement element;
        try {
            element = JsonParser.parseString(value);
        } catch (JsonSyntaxException e) {
            event.reply("You specified an invalid value! (`" + e.getMessage() + "`)").setEphemeral(true).queue();
            return;
        }

        log.info(event.getUser().getName() + " edited the config file!");
        JsonUtil.writeJsonFile(fileName, JsonUtil.setJsonValue(obj, key, element));
        event.reply("Successfully updated the JSON file!").setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "getusernames", subcommand = "nerds", description = "Get a list of all Minecraft names/UUIDs from Nerd roles in the server", defaultLocked = true)
    public void getNerdNames(GuildSlashEvent event) throws IOException {
        event.deferReply(true).queue();
        JsonArray array = getNames(event.getGuild(), true);
        File file = Util.createTempFile("uuids.txt", NerdBotApp.GSON.toJson(array));
        event.getHook().sendFiles(FileUpload.fromData(file)).queue();
    }

    @JDASlashCommand(name = "getusernames", subcommand = "all", description = "Get a list of all Minecraft names/UUIDs from members in the server", defaultLocked = true)
    public void getEveryonesNames(GuildSlashEvent event) throws IOException {
        event.deferReply(true).queue();
        JsonArray array = getNames(event.getGuild(), false);
        File file = Util.createTempFile("uuids.txt", NerdBotApp.GSON.toJson(array));
        event.getHook().sendFiles(FileUpload.fromData(file)).queue();
    }

    private JsonArray getNames(Guild guild, boolean nerdsOnly) {
        JsonArray jsonArray = new JsonArray();

        List<Member> members = guild.loadMembers().get()
                .stream()
                .filter(member -> Util.hasRole(member, "Member"))
                .toList();

        if (nerdsOnly) {
            members = members.stream().filter(member -> Util.hasRole(member, "Nerd") || Util.hasRole(member, "HPC") || Util.hasRole(member, "Grape")).toList();
        }

        log.info("Found " + members.size() + " members meeting requirements");
        members.forEach(member -> {
            // removes non-standard ascii characters from the discord nickname
            String plainUsername = member.getEffectiveName().trim().replaceAll("[^\u0000-\u007F]", "");
            String memberMCUsername = null;

            // checks if the member's username has flair
            if (!Pattern.matches(REGEX, plainUsername)) {
                // removes start and end characters ([example], {example}, |example| or (example)).
                // also strips spaces from the username
                plainUsername = plainUsername.replaceAll(SURROUND_REGEX, "").replace(" ", "");
                String[] splitUsername = plainUsername.split("[^a-zA-Z0-9_]");

                // gets the first item that matches the name constraints
                for (String item : splitUsername) {
                    if (Pattern.matches(REGEX, item)) {
                        memberMCUsername = item;
                        break;
                    }
                }
            } else {
                memberMCUsername = plainUsername.replace(" ", "");
            }

            // checks if there was a valid match found
            if (memberMCUsername != null) {
                log.info("Found match: " + memberMCUsername);
                usernameQueue.add(memberMCUsername);
                log.info(String.format("Added %s (%s) to the username lookup queue!", member.getEffectiveName(), memberMCUsername));
            } else {
                log.info(String.format("Didn't add %s to the username lookup queue", member.getEffectiveName()));
            }
        });

        while (!usernameQueue.isEmpty()) {
            try {
                String username = usernameQueue.poll();
                String response = sendRequest(username);
                JsonObject obj = NerdBotApp.GSON.fromJson(response, JsonObject.class);
                if (obj == null || obj.get("id") == null) {
                    log.info("Couldn't find UUID for " + username + "!");
                    continue;
                }
                jsonArray.add(obj.get("id").getAsString());
            } catch (IOException | URISyntaxException | InterruptedException e) {
                log.error("Encountered an error while looking up UUID: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return jsonArray;
    }

    private String sendRequest(String name) throws IOException, URISyntaxException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create(String.format("https://api.mojang.com/users/profiles/minecraft/%s", name))).GET().build();

        try {
            log.info("Sending request to " + httpRequest.uri());
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (InterruptedException e) {
            log.error(String.format("Encountered error while looking up Minecraft account of %s!", name));
            e.printStackTrace();
        }

        return null;
    }
}
