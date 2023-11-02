package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.slash.autocomplete.AutocompletionMode;
import com.freya02.botcommands.api.application.slash.autocomplete.annotations.AutocompletionHandler;
import com.google.gson.*;
import com.mongodb.client.FindIterable;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
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
import net.hypixel.nerdbot.api.repository.CachedMongoRepository;
import net.hypixel.nerdbot.bot.config.ChannelConfig;
import net.hypixel.nerdbot.bot.config.MetricsConfig;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.curator.ForumChannelCurator;
import net.hypixel.nerdbot.feature.ProfileUpdateFeature;
import net.hypixel.nerdbot.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.Environment;
import net.hypixel.nerdbot.util.JsonUtil;
import net.hypixel.nerdbot.util.LoggingUtil;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.exception.HttpException;
import net.hypixel.nerdbot.util.exception.ProfileMismatchException;
import net.hypixel.nerdbot.util.exception.RepositoryException;
import org.apache.logging.log4j.Level;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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


    @JDASlashCommand(name = "archive", subcommand = "channel", description = "Archives a specific channel.", defaultLocked = true)
    public void archive(GuildSlashEvent event, @AppOption TextChannel channel, @AppOption @Optional Boolean nerd, @AppOption @Optional Boolean alpha) {
        event.deferReply(true).complete();

        // By default nerd is true to prevent leaks.
        if (nerd == null) {
            nerd = true;
        }

        // By default, alpha is false.
        if (alpha == null) {
            alpha = false;
        }

        ChannelConfig channelConfig = NerdBotApp.getBot().getConfig().getChannelConfig();
        if (nerd) {
            Category nerdArchive = event.getGuild().getCategoryById(channelConfig.getNerdArchiveCategoryId());
            // Moves Channel to Nerd Archive category here.
            channel.getManager().setParent(nerdArchive).queue();
            channel.getManager().sync(nerdArchive.getPermissionContainer()).queue();
            event.getHook().editOriginal("Moved and Synced " + channel.getAsMention() + " to: `" + nerdArchive.getName() + "`").queue();
            return;
        }

        if (alpha) {
            Category alphaArchive = event.getGuild().getCategoryById(channelConfig.getAlphaArchiveCategoryId());
            // Moves Channel to Alpha Archive category here.
            channel.getManager().setParent(alphaArchive).queue();
            channel.getManager().sync(alphaArchive.getPermissionContainer()).queue();
            event.getHook().editOriginal("Moved and Synced " + channel.getAsMention() + " to: `" + alphaArchive.getName() + "`").queue();
            return;
        }

        Category publicArchive = event.getGuild().getCategoryById(channelConfig.getPublicArchiveCategoryId());
        // Moves Channel to Public Archive category here.
        channel.getManager().setParent(publicArchive).queue();
        channel.getManager().sync(publicArchive.getPermissionContainer()).queue();
        event.getHook().editOriginal("Moved and Synced " + channel.getAsMention() + " to: `" + publicArchive.getName() + "`").queue();
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
        PrometheusMetrics.setMetricsEnabled(bot.getConfig().getMetricsConfig().isEnabled());

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

    @JDASlashCommand(name = "metrics", subcommand = "toggle", description = "Toggle metrics collection", defaultLocked = true)
    public void toggleMetrics(GuildSlashEvent event) {
        Bot bot = NerdBotApp.getBot();
        MetricsConfig metricsConfig = bot.getConfig().getMetricsConfig();
        metricsConfig.setEnabled(!metricsConfig.isEnabled());
        PrometheusMetrics.setMetricsEnabled(metricsConfig.isEnabled());
        event.reply("Metrics collection is now " + (metricsConfig.isEnabled() ? "enabled" : "disabled") + "!").setEphemeral(true).queue();
    }

    @JDASlashCommand(
        name = "user",
        subcommand = "list",
        description = "Get all assigned Minecraft Names/UUIDs from all specified roles (requires Member) in the server.",
        defaultLocked = true
    )
    public void userList(GuildSlashEvent event, @Optional @AppOption(description = "Comma-separated role names to search for (default Member).") String roles) throws IOException {
        event.deferReply(true).complete();
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        String[] roleArray = roles != null ? roles.split(", ?") : new String[]{"Member"};
        JsonArray uuidArray = new JsonArray();

        List<MojangProfile> profiles = event.getGuild()
            .loadMembers()
            .get()
            .stream()
            .filter(member -> !member.getUser().isBot())
            .filter(member -> RoleManager.hasAnyRole(member, roleArray))
            .map(member -> discordUserRepository.findById(member.getId()))
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
        } catch (ProfileMismatchException exception) {
            event.getHook().sendMessage(exception.getMessage()).queue();
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
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        String missing = event.getGuild()
            .loadMembers()
            .get()
            .stream()
            .filter(member -> !member.getUser().isBot())
            .filter(member -> discordUserRepository.findById(member.getId()).noProfileAssigned())
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
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(member.getId());
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
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        List<MojangProfile> mojangProfiles = new ArrayList<>();

        event.getGuild()
            .loadMembers()
            .get()
            .stream()
            .filter(member -> !member.getUser().isBot())
            .filter(member -> discordUserRepository.findById(member.getId()).noProfileAssigned())
            .filter(member -> Util.getScuffedMinecraftIGN(member).isPresent())
            .forEach(member -> {
                String scuffedUsername = Util.getScuffedMinecraftIGN(member).orElseThrow();

                try {
                    MojangProfile mojangProfile = Util.getMojangProfile(scuffedUsername);
                    mojangProfiles.add(mojangProfile);
                    DiscordUser discordUser = discordUserRepository.findById(member.getId());
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

    @JDASlashCommand(name = "flared", description = "Add the Flared tag to a suggestion and lock it", defaultLocked = true)
    public void flareSuggestion(GuildSlashEvent event) {
        Channel channel = event.getChannel();

        if (!(channel instanceof ThreadChannel threadChannel) || !(threadChannel.getParentChannel() instanceof ForumChannel forumChannel)) {
            event.reply("This command can only be used inside a thread that is part of a forum!").setEphemeral(true).queue();
            return;
        }

        List<ForumTag> forumTags = forumChannel.getAvailableTagsByName("flared", true);

        if (forumTags.isEmpty()) {
            event.reply("This forum channel does not have the Flared tag!").setEphemeral(true).queue();
            return;
        }

        ForumTag forumTag = forumTags.get(0);

        if (threadChannel.getAppliedTags().contains(forumTag)) {
            event.reply("This suggestion is already flared!").setEphemeral(true).queue();
            return;
        }

        List<ForumTag> appliedTags = new ArrayList<>(threadChannel.getAppliedTags());
        appliedTags.add(forumTag);

        threadChannel.getManager()
            .setLocked(true)
            .setAppliedTags(appliedTags)
            .queue();

        event.reply(event.getUser().getAsMention() + " applied the " + forumTag.getName() + " tag and locked this suggestion!").queue();
    }

    @JDASlashCommand(name = "force-save", description = "Force save the database", defaultLocked = true)
    public void forceSaveRepository(GuildSlashEvent event, @AppOption String repository) {
        event.deferReply(true).complete();

        try {
            CachedMongoRepository<?> cachedMongoRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(repository);

            if (cachedMongoRepository == null) {
                event.getHook().editOriginal("Repository not found!").queue();
                return;
            }

            cachedMongoRepository.saveAllToDatabase();
            event.getHook().editOriginal("Saved " + cachedMongoRepository.getCache().estimatedSize() + " documents to the database!").queue();
        } catch (RepositoryException exception) {
            event.getHook().editOriginal("An error occurred while saving the repository: " + exception.getMessage()).queue();
            exception.printStackTrace();
        }
    }

    @JDASlashCommand(name = "transfer-tag", description = "Transfer forum tag to another.", defaultLocked = true)
    public void transferForumTag(
        GuildSlashEvent event,
        @AppOption(name = "channel", description = "Forum channel to transfer tags in.") ForumChannel channel,
        @AppOption(name = "from", description = "Transfer from this tag.", autocomplete = "forumtags") String from,
        @AppOption(name = "to", description = "Transfer to this tag.", autocomplete = "forumtags") String to
    ) {
        event.deferReply(false).complete();
        ForumTag fromTag;
        ForumTag toTag;

        try {
            // Autocomplete Support
            fromTag = Objects.requireNonNull(channel.getAvailableTagById(from));
            toTag = Objects.requireNonNull(channel.getAvailableTagById(to));
        } catch (NumberFormatException nfex) {
            try {
                // "I can type it myself" Support
                fromTag = channel.getAvailableTagsByName(from, true).get(0);
                toTag = channel.getAvailableTagsByName(to, true).get(0);
            } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
                event.getHook().editOriginal("You have entered invalid from/to tags, please try again.").complete();
                return;
            }
        }

        // Load Threads
        ForumTag searchTag = fromTag;
        List<ThreadChannel> threadChannels = Stream.concat(
                channel.getThreadChannels().stream(),
                channel.retrieveArchivedPublicThreadChannels().stream()
            )
            .filter(threadChannel -> threadChannel.getAppliedTags().contains(searchTag))
            .distinct() // Prevent Duplicates
            .toList();

        int total = threadChannels.size();
        if (total == 0) {
            event.getHook().editOriginal("No threads containing the `" + fromTag.getName() + "` tag were found!").complete();
            return;
        }

        int processed = 0;
        int modulo = Math.min(total, 10);
        event.getHook().editOriginal("Updated " + 0 + "/" + total + " threads...").complete();

        // Process Threads
        for (ThreadChannel threadChannel : threadChannels) {
            List<ForumTag> threadTags = new ArrayList<>(threadChannel.getAppliedTags());
            threadTags.remove(fromTag);

            // Prevent Duplicates
            if (!threadTags.contains(toTag)) {
                threadTags.add(toTag);
            }

            boolean archived = threadChannel.isArchived();

            if (archived) {
                threadChannel.getManager().setArchived(false).complete();
            }

            try {
                threadChannel.getManager().setAppliedTags(threadTags).complete();
            } catch (Exception ex) {
                log.warn("Unable to set applied tags for [" + threadChannel.getId() + "] " + threadChannel.getName(), ex);
            }

            if (archived) {
                threadChannel.getManager().setArchived(true).complete();
            }

            if (++processed % modulo == 0 && processed != total) {
                event.getHook().editOriginal("Updated " + processed + "/" + total + " threads...").complete();
            }
        }

        event.getHook().editOriginal("Finished transferring `" + fromTag.getName() + "` to `" + toTag.getName() + "` in " + total + " threads!").complete();
    }

    @AutocompletionHandler(name = "forumchannels", mode = AutocompletionMode.FUZZY, showUserInput = false)
    public List<ForumChannel> listForumChannels(CommandAutoCompleteInteractionEvent event) {
        return Util.getMainGuild().getForumChannels();
    }

    @AutocompletionHandler(name = "forumtags", mode = AutocompletionMode.FUZZY, showUserInput = false)
    public List<ForumTag> listForumTags(CommandAutoCompleteInteractionEvent event) {
        OptionMapping forumChannelId = event.getOption("channel");

        if (forumChannelId != null) {
            ForumChannel forumChannel = Util.getMainGuild().getForumChannelById(forumChannelId.getAsString());

            if (forumChannel != null) {
                return forumChannel.getAvailableTags();
            }
        }

        return List.of();
    }

    @JDASlashCommand(name = "loglevel", description = "Set the log level", defaultLocked = true)
    public void setLogLevel(GuildSlashEvent event, @AppOption(name = "level", description = "Log level to set", autocomplete = "loglevels") String level) {
        event.deferReply(true).complete();
        Level logLevel = Level.toLevel(level.toUpperCase());

        if (logLevel == null) {
            event.getHook().editOriginal("Invalid log level!").queue();
            return;
        }

        LoggingUtil.setGlobalLogLevel(logLevel);
        event.getHook().editOriginal("Set log level to " + logLevel + "!").queue();
    }

    @AutocompletionHandler(name = "loglevels", mode = AutocompletionMode.FUZZY, showUserInput = false)
    public List<String> listLogLevels(CommandAutoCompleteInteractionEvent event) {
        return Arrays.stream(Level.values()).map(Level::toString).toList();
    }
}
