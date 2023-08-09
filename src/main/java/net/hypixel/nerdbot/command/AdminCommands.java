package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.google.gson.*;
import com.mongodb.client.FindIterable;
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
import net.hypixel.nerdbot.feature.ProfileUpdateFeature;
import net.hypixel.nerdbot.util.Environment;
import net.hypixel.nerdbot.util.JsonUtil;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.gson.HttpException;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
            event.deferReply(true).complete();
            List<GreenlitMessage> output = forumChannelCurator.curate(channel);
            if (output.isEmpty()) {
                event.getHook().editOriginal("No suggestions were greenlit!").queue();
            } else {
                event.getHook().editOriginal("Greenlit " + output.size() + " suggestion(s) in " + (forumChannelCurator.getEndTime() - forumChannelCurator.getStartTime()) + "ms!").queue();
            }
        });
    }

    @JDASlashCommand(name = "invites", subcommand = "create", description = "Generate a bunch of invites for a specific channel.", defaultLocked = true)
    public void createInvites(GuildSlashEvent event, @AppOption int amount, @AppOption @Optional TextChannel channel) {
        List<Invite> invites = new ArrayList<>(amount);
        TextChannel selected = Objects.requireNonNullElse(channel, NerdBotApp.getBot().getJDA().getTextChannelsByName("limbo", true).get(0));
        event.deferReply(true).complete();

        if (ChannelManager.getLogChannel() != null) {
            ChannelManager.getLogChannel().sendMessageEmbeds(
                new EmbedBuilder()
                    .setTitle("Invites Created")
                    .setDescription(event.getUser().getAsMention() + " created " + amount + " invite(s) for " + selected.getAsMention() + ".")
                    .build()
            ).queue();
        }

        for (int i = 0; i < amount; i++) {
            try {
                InviteAction action = selected.createInvite()
                    .setUnique(true)
                    .setMaxAge(7L, TimeUnit.DAYS)
                    .setMaxUses(1);

                Invite invite = action.complete();
                invites.add(invite);
                log.info("Created new temporary invite '" + invite.getUrl() + "' for channel " + selected.getName() + " by " + event.getUser().getName());
            } catch (InsufficientPermissionException exception) {
                event.getHook().editOriginal("I don't have permission to create invites in " + selected.getAsMention() + "!").queue();
                return;
            }
        }

        StringBuilder stringBuilder = new StringBuilder("**Created " + invites.size() + " Invite(s):**\n");
        invites.forEach(invite -> stringBuilder.append(invite.getUrl()).append("\n"));
        event.getHook().editOriginal(stringBuilder.toString()).queue();
    }

    @JDASlashCommand(name = "invites", subcommand = "delete", description = "Delete all active invites.", defaultLocked = true)
    public void deleteInvites(GuildSlashEvent event) {
        event.deferReply(true).complete();

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
        event.deferReply(true).complete();
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
        subcommand = "link",
        description = "Link a Mojang Profile to a member's account.",
        defaultLocked = true
    )
    public void linkProfile(
        GuildSlashEvent event,
        @AppOption(description = "Member to link to.") Member member,
        @AppOption(description = "Your Minecraft IGN to link.") String username,
        @Optional @AppOption(description = "Bypass hypixel social check.") Boolean bypassSocial
    ) {
        event.deferReply(true).complete();
        bypassSocial = (bypassSocial == null || !bypassSocial);

        try {
            MojangProfile mojangProfile = MyCommands.requestMojangProfile(member, username, bypassSocial);
            MyCommands.updateMojangProfile(member, mojangProfile);
            event.getHook().sendMessage("Updated " + member.getAsMention() + "'s Mojang Profile to `" + mojangProfile.getUsername() + "` (`" + mojangProfile.getUniqueId() + "`).").queue();

            if (ChannelManager.getLogChannel() != null) {
                ChannelManager.getLogChannel()
                    .sendMessageEmbeds(
                        new EmbedBuilder()
                            .setAuthor(event.getMember().getEffectiveName() + " (" + event.getMember().getUser().getName() + ")")
                            .setTitle("Admin Mojang Profile Change")
                            .setThumbnail(member.getAvatarUrl())
                            .setDescription(event.getMember().getAsMention() + " updated the Mojang Profile for " + member.getAsMention() + ".")
                            .addField("Username", mojangProfile.getUsername(), false)
                            .addField(
                                "UUID / SkyCrypt",
                                String.format(
                                    "[%s](https://sky.shiiyu.moe/stats/%s)",
                                    mojangProfile.getUniqueId(),
                                    mojangProfile.getUniqueId()
                                ),
                                false
                            )
                            .build()
                    )
                    .queue();
            }
        } catch (HttpException exception) {
            event.getHook().sendMessage("Unable to locate Minecraft UUID for `" + username + "`: " + exception.getMessage()).queue();
            exception.printStackTrace();
        }
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

    @JDASlashCommand(
        name = "user",
        subcommand = "info",
        description = "View information about a user",
        defaultLocked = true
    )
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

                try {
                    MojangProfile mojangProfile = Util.getMojangProfile(scuffedUsername);
                    mojangProfiles.add(mojangProfile);
                    DiscordUser discordUser = Util.getOrAddUserToCache(database, member.getId());
                    discordUser.setMojangProfile(mojangProfile);
                    log.info("Migrated " + member.getEffectiveName() + " [" + member.getUser().getName() + "] (" + member.getId() + ") to " + mojangProfile.getUsername() + " (" + mojangProfile.getUniqueId() + ")");
                } catch (HttpException ex) {
                    log.warn("Unable to migrate " + member.getEffectiveName() + " [" + member.getUser().getName() + "] (" + member.getId() + ")");
                    ex.printStackTrace();
                }
            });

        event.getHook().sendMessage("Migrated " + mojangProfiles.size() + " Mojang Profiles to the database.").queue();
    }

    @JDASlashCommand(name = "user", subcommand = "update-nicks", description = "Update all user nicknames to match their Mojang Profile.", defaultLocked = true)
    public void updateNicknames(GuildSlashEvent event) {
        Database database = NerdBotApp.getBot().getDatabase();

        event.deferReply(true).queue();

    @JDASlashCommand(
        name = "user",
        subcommand = "info",
        description = "View information about a user",
        defaultLocked = true
    )
    public void userInfo(GuildSlashEvent event, @AppOption(description = "The user to search") Member member) {
        event.deferReply(true).complete();
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
        event.deferReply(true).complete();
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

                try {
                    MojangProfile mojangProfile = Util.getMojangProfile(scuffedUsername);
                    mojangProfiles.add(mojangProfile);
                    DiscordUser discordUser = Util.getOrAddUserToCache(database, member.getId());
                    discordUser.setMojangProfile(mojangProfile);
                    log.info("Migrated " + member.getEffectiveName() + " [" + member.getUser().getName() + "] (" + member.getId() + ") to " + mojangProfile.getUsername() + " (" + mojangProfile.getUniqueId() + ")");
                } catch (HttpException ex) {
                    log.warn("Unable to migrate " + member.getEffectiveName() + " [" + member.getUser().getName() + "] (" + member.getId() + ")");
                    ex.printStackTrace();
                }
            });

        event.getHook().sendMessage("Migrated " + mojangProfiles.size() + " Mojang Profiles to the database.").queue();
    }

    @JDASlashCommand(name = "user", subcommand = "update-nicks", description = "Update all user nicknames to match their Mojang Profile.", defaultLocked = true)
    public void updateNicknames(GuildSlashEvent event) {
        Database database = NerdBotApp.getBot().getDatabase();

        event.deferReply(true).complete();

        if (!database.isConnected()) {
            event.getHook().sendMessage("Database is not connected.").queue();
            return;
        }

        FindIterable<DiscordUser> users = database.findAllDocuments(database.getCollection("users", DiscordUser.class));

        if (users == null) {
            event.getHook().sendMessage("No users found.").queue();
            return;
        }

        users.forEach(discordUser -> {
            if (discordUser.isProfileAssigned()) {
                ProfileUpdateFeature.updateNickname(discordUser);
            }
        });
        event.getHook().editOriginal("Updated nicknames for " + users.into(new ArrayList<>()).size() + " users.").queue();
    }
}
