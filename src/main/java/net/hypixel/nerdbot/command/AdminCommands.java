package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.slash.autocomplete.AutocompletionMode;
import com.freya02.botcommands.api.application.slash.autocomplete.annotations.AutocompletionHandler;
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
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.restaction.InviteAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.internalapi.bot.Bot;
import net.hypixel.nerdbot.internalapi.bot.Environment;
import net.hypixel.nerdbot.internalapi.curator.Curator;
import net.hypixel.nerdbot.internalapi.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.internalapi.database.model.user.DiscordUser;
import net.hypixel.nerdbot.internalapi.database.model.user.language.UserLanguage;
import net.hypixel.nerdbot.internalapi.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.internalapi.language.TranslationManager;
import net.hypixel.nerdbot.internalapi.repository.Repository;
import net.hypixel.nerdbot.bot.config.MetricsConfig;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.curator.ForumChannelCurator;
import net.hypixel.nerdbot.feature.UserNominationFeature;
import net.hypixel.nerdbot.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.JsonUtil;
import net.hypixel.nerdbot.util.LoggingUtil;
import net.hypixel.nerdbot.util.TimeUtil;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.exception.HttpException;
import net.hypixel.nerdbot.util.exception.MojangProfileException;
import net.hypixel.nerdbot.util.exception.MojangProfileMismatchException;
import net.hypixel.nerdbot.util.exception.RepositoryException;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.logging.log4j.Level;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class AdminCommands extends ApplicationCommand {

    @JDASlashCommand(name = "curate", description = "Manually run the curation process", defaultLocked = true)
    public void curate(GuildSlashEvent event, @AppOption ForumChannel channel, @Optional @AppOption(description = "Run the curator without greenlighting suggestions") Boolean readOnly) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.reply(event, "generic.user_not_found");
            return;
        }

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            TranslationManager.reply(event, discordUser, "database.not_connected");
            log.error("Couldn't connect to the database!");
            return;
        }

        if (readOnly == null) {
            readOnly = false;
        }

        Curator<ForumChannel, ThreadChannel> forumChannelCurator = new ForumChannelCurator(readOnly);

        NerdBotApp.EXECUTOR_SERVICE.execute(() -> {
            event.deferReply(true).complete();

            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    NerdBotApp.EXECUTOR_SERVICE.execute(() -> {
                        if (forumChannelCurator.isCompleted()) {
                            this.cancel();
                            return;
                        }

                        if (forumChannelCurator.getCurrentObject() == null) {
                            return;
                        }

                        event.getHook().editOriginal("Export progress: " + forumChannelCurator.getIndex() + "/" + forumChannelCurator.getTotal()
                            + " in " + TimeUtil.formatMsCompact(System.currentTimeMillis() - forumChannelCurator.getStartTime())
                            + "\nCurrently looking at " + (forumChannelCurator.getCurrentObject().getJumpUrl())
                        ).queue();
                    });
                }
            };

            Timer timer = new Timer();
            timer.scheduleAtFixedRate(task, 0, 1_000);

            List<GreenlitMessage> output = forumChannelCurator.curate(channel);

            if (output.isEmpty()) {
                TranslationManager.edit(event.getHook(), discordUser, "curator.no_greenlit_messages");
            } else {
                TranslationManager.edit(event.getHook(), discordUser, "curator.greenlit_messages", output.size(), forumChannelCurator.getEndTime() - forumChannelCurator.getStartTime());
            }
        });
    }

    @JDASlashCommand(name = "invites", subcommand = "create", description = "Generate a bunch of invites for a specific channel.", defaultLocked = true)
    public void createInvites(GuildSlashEvent event, @AppOption int amount, @AppOption @Optional TextChannel channel) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.reply(event, "generic.user_not_found");
            return;
        }

        List<Invite> invites = new ArrayList<>(amount);
        TextChannel selected = Objects.requireNonNullElse(channel, NerdBotApp.getBot().getJDA().getTextChannelsByName("limbo", true).get(0));
        event.deferReply(true).complete();

        ChannelCache.getLogChannel().ifPresentOrElse(textChannel -> {
            textChannel.sendMessageEmbeds(
                new EmbedBuilder()
                    .setTitle("Invites Created")
                    .setDescription(event.getUser().getAsMention() + " created " + amount + " invite(s) for " + selected.getAsMention() + ".")
                    .build()
            ).queue();
        }, () -> log.warn("Log channel not found!"));

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
                TranslationManager.edit(event.getHook(), discordUser, "permissions.cannot_create_invites", selected.getAsMention());
                return;
            }
        }

        StringBuilder stringBuilder = new StringBuilder("**" + TranslationManager.translate(discordUser, "commands.invite.header", invites.size()) + "**\n");
        invites.forEach(invite -> stringBuilder.append(invite.getUrl()).append("\n"));
        event.getHook().editOriginal(stringBuilder.toString()).queue();
    }

    @JDASlashCommand(name = "invites", subcommand = "delete", description = "Delete all active invites.", defaultLocked = true)
    public void deleteInvites(GuildSlashEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findOrCreateById(event.getMember().getId());

        List<Invite> invites = event.getGuild().retrieveInvites().complete();
        invites.forEach(invite -> {
            invite.delete().complete();
            log.info(event.getUser().getName() + " deleted invite " + invite.getUrl());
        });

        ChannelCache.getLogChannel().ifPresentOrElse(textChannel -> {
            textChannel.sendMessageEmbeds(
                new EmbedBuilder()
                    .setTitle("Invites Deleted")
                    .setDescription(event.getUser().getAsMention() + " deleted all " + invites.size() + " invite(s).")
                    .build()
            ).queue();
        }, () -> {
            log.warn("Log channel not found!");
        });

        TranslationManager.edit(event.getHook(), discordUser, "commands.invite.deleted", invites.size());
    }

    @JDASlashCommand(name = "config", subcommand = "show", description = "View the currently loaded config", defaultLocked = true)
    public void showConfig(GuildSlashEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.edit(event.getHook(), "generic.user_not_found");
            return;
        }

        try {
            File file = Util.createTempFile("config-" + System.currentTimeMillis() + ".json", NerdBotApp.GSON.toJson(NerdBotApp.getBot().getConfig()));
            event.getHook().editOriginalAttachments(FileUpload.fromData(file)).queue();
        } catch (IOException exception) {
            TranslationManager.edit(event.getHook(), discordUser, "commands.config.read_error");
            log.error("An error occurred when reading the JSON file!", exception);
        }
    }

    @JDASlashCommand(name = "config", subcommand = "reload", description = "Reload the config file", defaultLocked = true)
    public void reloadConfig(GuildSlashEvent event) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.reply(event, "generic.user_not_found");
            return;
        }

        Bot bot = NerdBotApp.getBot();

        bot.loadConfig();
        bot.getJDA().getPresence().setActivity(Activity.of(bot.getConfig().getActivityType(), bot.getConfig().getActivity()));
        PrometheusMetrics.setMetricsEnabled(bot.getConfig().getMetricsConfig().isEnabled());

        TranslationManager.reply(event, discordUser, "commands.config.reloaded");
    }

    @JDASlashCommand(name = "config", subcommand = "edit", description = "Edit the config file", defaultLocked = true)
    public void editConfig(GuildSlashEvent event, @AppOption String key, @AppOption String value) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.reply(event, "generic.user_not_found");
            return;
        }

        // We should store the name of the config file on boot lol this is bad
        String fileName = System.getProperty("bot.config") != null ? System.getProperty("bot.config") : Environment.getEnvironment().name().toLowerCase() + ".config.json";
        JsonObject obj = JsonUtil.readJsonFile(fileName);

        if (obj == null) {
            TranslationManager.reply(event, discordUser, "commands.config.read_error");
            return;
        }

        JsonElement element;
        try {
            element = JsonParser.parseString(value);
        } catch (JsonSyntaxException exception) {
            TranslationManager.reply(event, discordUser, "commands.config.invalid_value", exception.getMessage());
            return;
        }

        JsonUtil.writeJsonFile(fileName, JsonUtil.setJsonValue(obj, key, element));
        log.info(event.getUser().getName() + " edited the config file!");
        TranslationManager.reply(event, discordUser, "commands.config.updated");
    }

    @JDASlashCommand(name = "translations", subcommand = "reload", description = "Reload the translations file", defaultLocked = true)
    public void reloadTranslations(GuildSlashEvent event, @AppOption(autocomplete = "languages") @Optional UserLanguage language) {
        event.deferReply(true).complete();

        if (language == null) {
            for (UserLanguage value : UserLanguage.VALUES) {
                TranslationManager.reloadTranslations(value);
                TranslationManager.edit(event.getHook(), "commands.translations.reloaded", value.name());

                // Set to different message once done
                if (value.ordinal() == UserLanguage.VALUES.length - 1) {
                    TranslationManager.edit(event.getHook(), "commands.translations.reload_complete");
                }
            }
        } else {
            TranslationManager.reloadTranslations(language);
            TranslationManager.edit(event.getHook(), "commands.translations.reloaded", language.name());
        }
    }

    @JDASlashCommand(name = "metrics", subcommand = "toggle", description = "Toggle metrics collection", defaultLocked = true)
    public void toggleMetrics(GuildSlashEvent event) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.reply(event, "generic.user_not_found");
            return;
        }

        Bot bot = NerdBotApp.getBot();
        MetricsConfig metricsConfig = bot.getConfig().getMetricsConfig();
        metricsConfig.setEnabled(!metricsConfig.isEnabled());
        PrometheusMetrics.setMetricsEnabled(metricsConfig.isEnabled());

        TranslationManager.reply(event, discordUser, "commands.metrics.toggle", metricsConfig.isEnabled());
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

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(member.getId());

        if (discordUser == null) {
            TranslationManager.reply(event, "generic.user_not_found");
            return;
        }

        bypassSocial = (bypassSocial == null || !bypassSocial);

        try {
            MojangProfile mojangProfile = ProfileCommands.requestMojangProfile(member, username, bypassSocial);
            ProfileCommands.updateMojangProfile(member, mojangProfile);
            TranslationManager.edit(event.getHook(), discordUser, "commands.user.linked_by_admin", member.getAsMention(), mojangProfile.getUsername(), mojangProfile.getUniqueId());

            ChannelCache.getLogChannel().ifPresentOrElse(textChannel -> {
                textChannel.sendMessageEmbeds(
                    new EmbedBuilder()
                        .setTitle("Mojang Profile Change")
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
                ).queue();
            }, () -> {
                log.warn("Log channel not found!");
            });
        } catch (HttpException exception) {
            TranslationManager.edit(event.getHook(), discordUser, "commands.user.username_not_found", username, exception.getMessage());
            log.error("Unable to locate Minecraft UUID for " + username + "!", exception);
        } catch (MojangProfileMismatchException exception) {
            TranslationManager.edit(event.getHook(), discordUser, "commands.user.profile_mismatch", username, exception.getMessage());
        } catch (MojangProfileException exception) {
            event.getHook().editOriginal(exception.getMessage()).queue();
            log.error("An error occurred when fetching the Mojang profile for " + member.getId() + "!", exception);
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
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.edit(event.getHook(), "generic.user_not_found");
            return;
        }

        String missing = event.getGuild()
            .loadMembers()
            .get()
            .stream()
            .filter(member -> !member.getUser().isBot())
            .filter(member -> discordUserRepository.findById(member.getId()).noProfileAssigned())
            .map(IMentionable::getAsMention)
            .collect(Collectors.joining(", "));

        if (!missing.isEmpty()) {
            event.getHook().editOriginalEmbeds(
                new EmbedBuilder()
                    .setColor(Color.MAGENTA)
                    .setTitle("Missing Mojang Profiles")
                    .setDescription(missing)
                    .build()
            ).queue();
        } else {
            TranslationManager.edit(event.getHook(), discordUser, "commands.user.no_missing_profiles");
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
        List<MessageEmbed> embeds = new ArrayList<>(ProfileCommands.getActivityEmbeds(member));

        String profile = discordUser.isProfileAssigned() ?
            discordUser.getMojangProfile().getUsername() + " (" + discordUser.getMojangProfile().getUniqueId().toString() + ")" :
            "*Missing Data*";

        embeds.add(0, new EmbedBuilder()
            .setAuthor(member.getEffectiveName())
            .setThumbnail(member.getEffectiveAvatarUrl())
            .addField("ID", member.getId(), false)
            .addField("Mojang Profile", profile, false)
            .addField("Language", discordUser.getLanguage().getName(), false)
            .addField("Birthday", (discordUser.getBirthdayData().isBirthdaySet() ? DateFormatUtils.format(discordUser.getBirthdayData().getBirthday(), "dd MMMM yyyy") : "Not Set"), false)
            .build());
        embeds.add(1, ProfileCommands.createBadgesEmbed(member, discordUser, false));

        event.getHook().editOriginalEmbeds(embeds.toArray(new MessageEmbed[]{})).queue();
    }

    @JDASlashCommand(
        name = "user",
        subcommand = "badges",
        description = "View the badges of a user",
        defaultLocked = true
    )
    public void viewUserBadges(GuildSlashEvent event, @AppOption(description = "The user to search") Member member) {
        event.deferReply(true).queue();
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(member.getId());

        if (discordUser == null) {
            TranslationManager.edit(event.getHook(), "generic.user_not_found");
            return;
        }

        event.getHook().editOriginalEmbeds(ProfileCommands.createBadgesEmbed(member, discordUser, event.getUser().getId().equals(member.getId()))).queue();
    }

    @JDASlashCommand(
        name = "user",
        group = "migrate",
        subcommand = "names",
        description = "Attempts to migrate any user with no assigned Mojang Profile using their display name.",
        defaultLocked = true
    )
    public void migrateUsernames(GuildSlashEvent event) {
        event.deferReply(true).complete();
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.edit(event.getHook(), "generic.user_not_found");
            return;
        }

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
                    DiscordUser user = discordUserRepository.findById(member.getId());
                    user.setMojangProfile(mojangProfile);
                    log.info("Migrated " + member.getEffectiveName() + " [" + member.getUser().getName() + "] (" + member.getId() + ") to " + mojangProfile.getUsername() + " (" + mojangProfile.getUniqueId() + ")");
                } catch (HttpException exception) {
                    log.error("Unable to migrate " + member.getEffectiveName() + "(ID: " + member.getId() + ")", exception);
                }
            });

        TranslationManager.edit(event.getHook(), discordUser, "commands.user.migrated_profiles", mojangProfiles.size());
    }

    @JDASlashCommand(name = "cache", subcommand = "force-save", description = "Force save the specified cache to the database", defaultLocked = true)
    public void forceSaveRepository(GuildSlashEvent event, @AppOption String repositoryName) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            TranslationManager.edit(event.getHook(), "generic.user_not_found");
            return;
        }

        try {
            Repository<?> repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(repositoryName);
            if (repository == null) {
                TranslationManager.edit(event.getHook(), discordUser, "repository.not_found");
                return;
            }

            repository.saveAllToDatabase();
            TranslationManager.edit(event.getHook(), discordUser, "repository.saved_to_database", repository.getCache().estimatedSize());
        } catch (RepositoryException exception) {
            TranslationManager.edit(event.getHook(), discordUser, "repository.save_error", exception.getMessage());
            log.error("An error occurred while saving the repository!", exception);
        }
    }

    @JDASlashCommand(name = "cache", subcommand = "force-load", description = "Forcefully load documents from the database into the cache", defaultLocked = true)
    public void forceLoadDocuments(GuildSlashEvent event, @AppOption String repositoryName) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            TranslationManager.edit(event.getHook(), "generic.user_not_found");
            return;
        }

        try {
            Repository<?> repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(repositoryName);

            if (repository == null) {
                TranslationManager.edit(event.getHook(), discordUser, "generic.user_not_found");
                return;
            }

            repository.loadAllDocumentsIntoCache();
            TranslationManager.edit(event.getHook(), discordUser, "repository.loaded_from_database", repository.getCache().estimatedSize());
        } catch (RepositoryException exception) {
            TranslationManager.edit(event.getHook(), discordUser, "repository.load_error", exception.getMessage());
            log.error("An error occurred while saving the repository!", exception);
        }
    }

    @JDASlashCommand(name = "cache", subcommand = "stats", description = "View cache statistics", defaultLocked = true)
    public void cacheStats(GuildSlashEvent event, @AppOption String repositoryName) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findOrCreateById(event.getUser().getId());

        try {
            Repository<?> repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(repositoryName);

            if (repository == null) {
                TranslationManager.edit(event.getHook(), discordUser, "repository.not_found");
                return;
            }

            event.getHook().editOriginal(repository.getCache().stats().toString()).queue();
        } catch (RepositoryException exception) {
            TranslationManager.edit(event.getHook(), discordUser, "repository.stats_error", exception.getMessage());
            log.error("An error occurred while saving the repository!", exception);
        }
    }

    @JDASlashCommand(name = "transfer-tag", description = "Transfer forum tag to another.", defaultLocked = true)
    public void transferForumTag(
        GuildSlashEvent event,
        @AppOption(name = "channel", description = "Forum channel to transfer tags in.") ForumChannel channel,
        @AppOption(name = "from", description = "Transfer from this tag.", autocomplete = "forumtags") String from,
        @AppOption(name = "to", description = "Transfer to this tag.", autocomplete = "forumtags") String to
    ) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            TranslationManager.edit(event.getHook(), "generic.user_not_found");
            return;
        }

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
                TranslationManager.edit(event.getHook(), discordUser, "commands.transfer_tag.invalid_tags");
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
            TranslationManager.edit(event.getHook(), discordUser, "commands.transfer_tag.no_threads_found", fromTag.getName());
            return;
        }

        int processed = 0;
        int modulo = Math.min(total, 10);
        TranslationManager.edit(event.getHook(), discordUser, "commands.transfer_tag.updated_threads", 0, total);

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
            } catch (Exception exception) {
                log.warn("Unable to set applied tags for [" + threadChannel.getId() + "] " + threadChannel.getName(), exception);
            }

            if (archived) {
                threadChannel.getManager().setArchived(true).complete();
            }

            if (++processed % modulo == 0 && processed != total) {
                TranslationManager.edit(event.getHook(), discordUser, "commands.transfer_tag.updated_threads", processed, total);
            }
        }

        TranslationManager.edit(event.getHook(), discordUser, "commands.transfer_tag.updated_threads_complete", fromTag.getName(), toTag.getName(), total);
    }

    @AutocompletionHandler(name = "forumchannels", mode = AutocompletionMode.FUZZY, showUserInput = false)
    public List<ForumChannel> listForumChannels(CommandAutoCompleteInteractionEvent event) {
        return Util.getMainGuild().getForumChannels();
    }

    @AutocompletionHandler(name = "forumtags", mode = AutocompletionMode.FUZZY, showUserInput = false)
    public List<ForumTag> listForumTags(CommandAutoCompleteInteractionEvent event) {
        List<ForumTag> forumTags = new ArrayList<>();

        ChannelCache.getForumChannelById(event.getOption("channel").getAsString()).ifPresent(forumChannel -> {
            forumTags.addAll(forumChannel.getAvailableTags());
        });

        return forumTags;
    }

    @JDASlashCommand(name = "loglevel", description = "Set the log level", defaultLocked = true)
    public void setLogLevel(GuildSlashEvent event, @AppOption(name = "level", description = "Log level to set", autocomplete = "loglevels") String level) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            TranslationManager.edit(event.getHook(), "generic.user_not_found");
            return;
        }

        Level logLevel = Level.toLevel(level.toUpperCase());

        if (logLevel == null) {
            TranslationManager.edit(event.getHook(), discordUser, "commands.log_level.invalid_level");
            return;
        }

        LoggingUtil.setGlobalLogLevel(logLevel);
        TranslationManager.edit(event.getHook(), discordUser, "commands.log_level.set_level", logLevel);
    }

    @AutocompletionHandler(name = "loglevels", mode = AutocompletionMode.FUZZY, showUserInput = false)
    public List<String> listLogLevels(CommandAutoCompleteInteractionEvent event) {
        return Arrays.stream(Level.values()).map(Level::toString).toList();
    }

    @JDASlashCommand(name = "force", subcommand = "nominations", description = "Forcefully run the nomination process", defaultLocked = true)
    public void forceNominations(GuildSlashEvent event) {
        UserNominationFeature.nominateUsers();
        TranslationManager.reply(event, "commands.force_nominations.success");
    }
}
