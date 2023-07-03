package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.prefixed.annotations.TextOption;
import com.google.gson.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
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
import net.hypixel.nerdbot.util.minecraft.Minecraft;

import javax.xml.namespace.QName;
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
            try {
                InviteAction action = channel.createInvite()
                    .setUnique(true)
                    .setMaxAge(7L, TimeUnit.DAYS)
                    .setMaxUses(1);

                Invite invite = action.complete();
                invites.add(invite);
                log.info("Generated new temporary invite '" + invite.getUrl() + "' for channel " + channel.getName() + " by " + event.getUser().getAsTag());
            } catch (InsufficientPermissionException exception) {
                event.getHook().editOriginal("I don't have permission to create invites in " + channel.getAsMention() + "!").queue();
                return;
            }
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

    @JDASlashCommand(name = "getuuids", description = "Get a list of all Minecraft UUIDs from a role in the server", defaultLocked = true)
    public void getNerdNames(GuildSlashEvent event, @AppOption(description = "The role to fetch for") Role role) throws IOException {
        event.deferReply(true).queue();
        JsonArray array = Minecraft.getUUIDs(event.getGuild(), role);
        File file = Util.createTempFile("uuids.txt", NerdBotApp.GSON.toJson(array));
        event.getHook().sendFiles(FileUpload.fromData(file)).queue();
    }

    @JDASlashCommand(name = "getuuid", description = "Get a single members Minecraft UUID")
    public void getUUID(GuildSlashEvent event, @AppOption(description = "The user to fetch") Member member) {
        String username = Minecraft.getName(member);
        String uuid = Minecraft.getUUID(username);
        event.reply(String.format("%s: %s", username, uuid)).setEphemeral(true).queue();
    }

}
