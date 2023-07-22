package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.restaction.InviteAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.bot.Bot;
import net.hypixel.nerdbot.api.curator.Curator;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.curator.ForumChannelCurator;
import net.hypixel.nerdbot.util.Environment;
import net.hypixel.nerdbot.util.JsonUtil;
import net.hypixel.nerdbot.util.Util;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    @JDASlashCommand(name = "invites", subcommand = "create", description = "Generate a bunch of invites for a specific channel.", defaultLocked = true)
    public void createInvites(GuildSlashEvent event, @AppOption int amount, @AppOption TextChannel channel) {
        List<Invite> invites = new ArrayList<>(amount);
        event.deferReply(true).queue();

        if (ChannelManager.getLogChannel() != null) {
            ChannelManager.getLogChannel().sendMessageEmbeds(
                new EmbedBuilder()
                    .setTitle("Invites Created")
                    .setDescription(event.getUser().getAsMention() + " created " + amount + " invite(s) for " + channel.getAsMention() + ".")
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
                log.info("Created new temporary invite '" + invite.getUrl() + "' for channel " + channel.getName() + " by " + event.getUser().getName());
            } catch (InsufficientPermissionException exception) {
                event.getHook().editOriginal("I don't have permission to create invites in " + channel.getAsMention() + "!").queue();
                return;
            }
        }

        StringBuilder stringBuilder = new StringBuilder("**Created " + invites.size() + " Invite(s):**\n");
        invites.forEach(invite -> stringBuilder.append(invite.getUrl()).append("\n"));
        event.getHook().editOriginal(stringBuilder.toString()).queue();
    }

    @JDASlashCommand(name = "invites", subcommand = "delete", description = "Delete all active invites.", defaultLocked = true)
    public void deleteInvites(GuildSlashEvent event) {
        event.deferReply(true).queue();

        List<Invite> invites = event.getGuild().retrieveInvites().complete();
        invites.forEach(invite -> {
            invite.delete().complete();
            log.info(event.getUser().getName() + " deleted invite " + invite.getUrl());
        });

        if (ChannelManager.getLogChannel() != null) {
            ChannelManager.getLogChannel().sendMessageEmbeds(
                new EmbedBuilder()
                    .setTitle("Invites Deleted")
                    .setDescription(event.getUser().getAsMention() + " deleted all " + invites.size() + " invite(s).")
                    .build()
            ).queue();
        }
        event.getHook().editOriginal("Deleted " + invites.size() + " invites.").queue();
    }

    @JDASlashCommand(name = "config", subcommand = "show", description = "View the currently loaded config", defaultLocked = true)
    public void showConfig(GuildSlashEvent event) {
        event.reply("```json\n" + NerdBotApp.GSON.toJson(NerdBotApp.getBot().getConfig()) + "```").setEphemeral(true).queue();
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

        JsonUtil.writeJsonFile(fileName, JsonUtil.setJsonValue(obj, key, element));
        log.info(event.getUser().getName() + " edited the config file!");
        event.reply("Successfully updated the JSON file!").setEphemeral(true).queue();
    }

    @JDASlashCommand(
        name = "user",
        subcommand = "list",
        description = "Get all assigned Minecraft Names/UUIDs from all specified roles (requires Member) in the server.",
        defaultLocked = true
    )
    public void userList(GuildSlashEvent event, @Optional @AppOption(description = "Comma-separated role names to search for (default Member).") String roles) throws IOException {
        event.deferReply(true).queue();
        Database database = NerdBotApp.getBot().getDatabase();
        String[] roleArray = roles != null ? roles.split(", ?") : new String[] { "Member" };
        JsonArray uuidArray = new JsonArray();

        List<MojangProfile> profiles = event.getGuild()
            .loadMembers()
            .get()
            .stream()
            .filter(member -> !member.getUser().isBot())
            .filter(member -> Util.hasAnyRole(member, roleArray))
            .map(member -> Util.getOrAddUserToCache(database, member.getId()))
            .filter(DiscordUser::isProfileAssigned)
            .map(DiscordUser::getMojangProfile)
            .toList();

        log.info("Found " + profiles.size() + " members meeting requirements.");
        profiles.forEach(profile -> uuidArray.add(profile.getUniqueId().toString()));
        File file = Util.createTempFile("uuids.txt", NerdBotApp.GSON.toJson(uuidArray));
        event.getHook().sendFiles(FileUpload.fromData(file)).queue();
    }

    @JDASlashCommand(
        name = "user",
        subcommand = "missing",
        description = "List any user with no assigned Mojang Profile.",
        defaultLocked = true
    )
    public void userMissingProfile(GuildSlashEvent event) {
        event.deferReply(true).queue();
        Database database = NerdBotApp.getBot().getDatabase();

        String missing = event.getGuild()
            .loadMembers()
            .get()
            .stream()
            .filter(member -> !member.getUser().isBot())
            .filter(member -> Util.getOrAddUserToCache(database, member.getId()).noProfileAssigned())
            .map(IMentionable::getAsMention)
            .collect(Collectors.joining(", "));

        if (missing.length() > 0) {
            event.getHook().editOriginalEmbeds(
                new EmbedBuilder()
                    .setColor(Color.MAGENTA)
                    .setTitle("Missing Mojang Profiles")
                    .setDescription(missing)
                    .build()
            ).queue();
        } else {
            event.getHook().editOriginal("There are no missing Mojang Profiles.").queue();
        }
    }

    @JDASlashCommand(name = "user", subcommand = "info", description = "View information about a user", defaultLocked = true)
    public void userInfo(GuildSlashEvent event, @AppOption(description = "The user to search") Member member) {
        event.deferReply(true).queue();
        Database database = NerdBotApp.getBot().getDatabase();
        DiscordUser discordUser = Util.getOrAddUserToCache(database, member.getId());
        Pair<EmbedBuilder, EmbedBuilder> activityEmbeds = MyCommands.getActivityEmbeds(member);

        String profile = discordUser.isProfileAssigned() ?
            discordUser.getMojangProfile().getUsername() + " (" + discordUser.getMojangProfile().getUniqueId().toString() + ")" :
            "*Missing Data*";

        event.getHook().editOriginalEmbeds(
            new EmbedBuilder()
                .setAuthor(member.getEffectiveName())
                .setThumbnail(member.getEffectiveAvatarUrl())
                .addField("ID", member.getId(), false)
                .addField("Mojang Profile", profile, false)
                .build(),
            activityEmbeds.getLeft().build(),
            activityEmbeds.getRight().build()
        ).queue();
    }

    @JDASlashCommand(
        name = "user",
        subcommand = "migrate",
        description = "Attempts to migrate any user with no assigned Mojang Profile using their display name.",
        defaultLocked = true
    )
    public void migrateUsernames(GuildSlashEvent event) {
        event.deferReply(true).queue();
        Database database = NerdBotApp.getBot().getDatabase();
        List<MojangProfile> mojangProfiles = new ArrayList<>();

        event.getGuild()
            .loadMembers()
            .get()
            .stream()
            .filter(member -> !member.getUser().isBot())
            .filter(member -> Util.getOrAddUserToCache(database, member.getId()).noProfileAssigned())
            .filter(member -> Util.getScuffedMinecraftIGN(member).isPresent())
            .forEach(member -> {
                String scuffedUsername = Util.getScuffedMinecraftIGN(member).orElseThrow();
                java.util.Optional<MojangProfile> mojangProfile = Util.getMojangProfile(scuffedUsername);

                if (mojangProfile.isPresent()) {
                    mojangProfiles.add(mojangProfile.get());
                    DiscordUser discordUser = Util.getOrAddUserToCache(database, member.getId());
                    discordUser.setMojangProfile(mojangProfile.get());
                }
            });

        event.getHook().sendMessage("Migrated " + mojangProfiles.size() + " Mojang Profiles to the database.").queue();
    }

}
