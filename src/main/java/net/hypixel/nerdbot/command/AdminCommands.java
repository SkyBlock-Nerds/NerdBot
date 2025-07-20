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
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.restaction.InviteAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.bot.Bot;
import net.hypixel.nerdbot.api.bot.Environment;
import net.hypixel.nerdbot.api.curator.Curator;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.language.UserLanguage;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.api.database.model.user.stats.NominationInfo;
import net.hypixel.nerdbot.api.language.TranslationManager;
import net.hypixel.nerdbot.api.repository.Repository;
import net.hypixel.nerdbot.bot.config.BotConfig;
import net.hypixel.nerdbot.bot.config.MetricsConfig;
import net.hypixel.nerdbot.bot.config.channel.ChannelConfig;
import net.hypixel.nerdbot.bot.config.objects.RoleRestrictedChannelGroup;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.curator.ForumChannelCurator;
import net.hypixel.nerdbot.feature.UserNominationFeature;
import net.hypixel.nerdbot.listener.RoleRestrictedChannelListener;
import net.hypixel.nerdbot.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.DiscordUtils;
import net.hypixel.nerdbot.util.FileUtils;
import net.hypixel.nerdbot.util.JsonUtils;
import net.hypixel.nerdbot.util.LoggingUtils;
import net.hypixel.nerdbot.util.TimeUtils;
import net.hypixel.nerdbot.util.HttpUtils;
import net.hypixel.nerdbot.util.Utils;
import net.hypixel.nerdbot.util.exception.HttpException;
import net.hypixel.nerdbot.util.exception.MojangProfileException;
import net.hypixel.nerdbot.util.exception.MojangProfileMismatchException;
import net.hypixel.nerdbot.util.exception.RepositoryException;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.logging.log4j.Level;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class AdminCommands extends ApplicationCommand {

    @JDASlashCommand(name = "curate", description = "Manually run the curation process", defaultLocked = true)
    public void curate(GuildSlashEvent event, @AppOption ForumChannel channel, @Optional @AppOption(description = "Run the curator without greenlighting suggestions") Boolean readOnly) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        final boolean finalReadOnly = readOnly != null && readOnly;

        discordUserRepository.findByIdAsync(event.getMember().getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    TranslationManager.reply(event, "generic.user_not_found");
                    return;
                }

                if (!NerdBotApp.getBot().getDatabase().isConnected()) {
                    TranslationManager.reply(event, discordUser, "database.not_connected");
                    log.error("Couldn't connect to the database!");
                    return;
                }

                Curator<ForumChannel, ThreadChannel> forumChannelCurator = new ForumChannelCurator(finalReadOnly);

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
                                    + " in " + TimeUtils.formatMsCompact(System.currentTimeMillis() - forumChannelCurator.getStartTime())
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
            })
            .exceptionally(throwable -> {
                log.error("Error loading user for curation", throwable);
                TranslationManager.reply(event, "Failed to load user data");
                return null;
            });
    }

    @JDASlashCommand(name = "invites", subcommand = "create", description = "Generate a bunch of invites for a specific channel.", defaultLocked = true)
    public void createInvites(GuildSlashEvent event, @AppOption int amount, @AppOption @Optional TextChannel channel) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        discordUserRepository.findByIdAsync(event.getMember().getId())
            .thenAccept(discordUser -> {
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
                event.getHook().editOriginal(stringBuilder.toString()).queue();            })
            .exceptionally(throwable -> {
                log.error("Error loading user for invite creation", throwable);
                TranslationManager.reply(event, "Failed to load user data");
                return null;
            });
    }

    @JDASlashCommand(name = "invites", subcommand = "delete", description = "Delete all active invites.", defaultLocked = true)
    public void deleteInvites(GuildSlashEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        discordUserRepository.findOrCreateByIdAsync(event.getMember().getId())
            .thenAccept(discordUser -> {
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

                TranslationManager.edit(event.getHook(), discordUser, "commands.invite.deleted", invites.size());            })
            .exceptionally(throwable -> {
                log.error("Error loading user for invite deletion", throwable);
                event.getHook().editOriginal("Failed to load user data").queue();
                return null;
            });
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
            File file = FileUtils.createTempFile("config-" + System.currentTimeMillis() + ".json", NerdBotApp.GSON.toJson(NerdBotApp.getBot().getConfig()));
            event.getHook().editOriginalAttachments(FileUpload.fromData(file)).queue();
        } catch (IOException exception) {
            TranslationManager.edit(event.getHook(), discordUser, "commands.config.read_error");
            log.error("An error occurred when reading the JSON file!", exception);
        }
    }

    @JDASlashCommand(name = "config", subcommand = "reload", description = "Reload the config file", defaultLocked = true)
    public void reloadConfig(GuildSlashEvent event) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        discordUserRepository.findByIdAsync(event.getMember().getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    TranslationManager.reply(event, "generic.user_not_found");
                    return;
                }

                event.deferReply().setEphemeral(true).complete();

                Bot bot = NerdBotApp.getBot();
                bot.loadConfig();
                bot.getJDA().getPresence().setActivity(Activity.of(bot.getConfig().getActivityType(), bot.getConfig().getActivity()));
                PrometheusMetrics.setMetricsEnabled(bot.getConfig().getMetricsConfig().isEnabled());

                event.getHook().editOriginal("Reloaded the config file!").queue();
            })
            .exceptionally(throwable -> {
                log.error("Error loading user for config reload", throwable);
                TranslationManager.reply(event, "Failed to load user data");
                return null;
            });
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

        JsonUtils.readJsonFileAsync(fileName)
            .thenCompose(obj -> {
                if (obj == null) {
                    return CompletableFuture.failedFuture(new RuntimeException("Failed to read config file"));
                }

                JsonElement element;
                try {
                    element = JsonParser.parseString(value);
                } catch (JsonSyntaxException exception) {
                    return CompletableFuture.failedFuture(exception);
                }

                return JsonUtils.writeJsonFileAsync(fileName, JsonUtil.setJsonValue(obj, key, element));
            })
            .thenRun(() -> {
                log.info(event.getUser().getName() + " edited the config file!");
                TranslationManager.reply(event, discordUser, "commands.config.updated");
            })
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof JsonSyntaxException) {
                    TranslationManager.reply(event, discordUser, "commands.config.invalid_value", throwable.getCause().getMessage());
                } else {
                    log.error("Error processing config file", throwable);
                    TranslationManager.reply(event, discordUser, "commands.config.read_error");
                }
                return null;
            });
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

        discordUserRepository.findByIdAsync(member.getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    TranslationManager.reply(event, "generic.user_not_found");
                    return;
                }

                boolean enforceSocial = bypassSocial == null || !bypassSocial;

                ProfileCommands.requestMojangProfileAsync(member, username, enforceSocial)
                    .thenAccept(mojangProfile -> {
                        if (mojangProfile == null) {
                            TranslationManager.edit(event.getHook(), discordUser, "commands.user.username_not_found", username, "Profile not found");
                            return;
                        }

                        if (mojangProfile.getErrorMessage() != null) {
                            if (mojangProfile.getErrorMessage().contains("does not match")) {
                                TranslationManager.edit(event.getHook(), discordUser, "commands.user.profile_mismatch", username, mojangProfile.getErrorMessage());
                            } else {
                                event.getHook().editOriginal(mojangProfile.getErrorMessage()).queue();
                            }
                            return;
                        }

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
                    })
                    .exceptionally(throwable -> {
                        log.error("Error during profile linking", throwable);
                        TranslationManager.edit(event.getHook(), discordUser, "commands.user.username_not_found", username, throwable.getMessage());
                        return null;
                    });
            })
            .exceptionally(throwable -> {
                log.error("Error loading user for profile linking", throwable);
                TranslationManager.edit(event.getHook(), "Failed to load user data");
                return null;
            });
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

        event.getGuild()
            .loadMembers()
            .onSuccess(members -> {
                List<CompletableFuture<String>> memberChecks = members.stream()
                    .filter(member -> !member.getUser().isBot())
                    .map(member ->
                        discordUserRepository.findByIdAsync(member.getId())
                            .thenApply(discordUser -> {
                                if (discordUser != null && discordUser.noProfileAssigned()) {
                                    return member.getAsMention();
                                }
                                return null;
                            })
                    )
                    .toList();

                CompletableFuture.allOf(memberChecks.toArray(new CompletableFuture[0]))
                    .thenRun(() -> {
                        String missing = memberChecks.stream()
                            .map(CompletableFuture::join)
                            .filter(Objects::nonNull)
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
                            event.getHook().editOriginal("No users with missing Mojang Profiles found.").queue();
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("Error checking missing profiles", throwable);
                        event.getHook().editOriginal("An error occurred while checking for missing profiles: " + throwable.getMessage()).queue();
                        return null;
                    });
            })
            .onError(throwable -> {
                log.error("Failed to load guild members", throwable);
                event.getHook().editOriginal("Failed to load guild members").queue();
            });
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
            .onSuccess(members -> {
                List<CompletableFuture<Void>> migrationTasks = members.stream()
                    .filter(member -> !member.getUser().isBot())
                    .filter(member -> Utils.getScuffedMinecraftIGN(member).isPresent())
                    .map(member ->
                        discordUserRepository.findByIdAsync(member.getId())
                            .thenCompose(user -> {
                                if (user == null || !user.noProfileAssigned()) {
                                    return CompletableFuture.completedFuture(null);
                                }

                                String scuffedUsername = Utils.getScuffedMinecraftIGN(member).orElseThrow();
                                return Utils.getMojangProfileAsync(scuffedUsername)
                                    .thenAccept(mojangProfile -> {
                                        if (mojangProfile != null) {
                                            mojangProfiles.add(mojangProfile);
                                            user.setMojangProfile(mojangProfile);
                                            log.info("Migrated " + member.getEffectiveName() + " [" + member.getUser().getName() + "] (" + member.getId() + ") to " + mojangProfile.getUsername() + " (" + mojangProfile.getUniqueId() + ")");
                                        }
                                    })
                                    .exceptionally(throwable -> {
                                        log.error("Unable to migrate " + member.getEffectiveName() + "(ID: " + member.getId() + ")", throwable);
                                        return null;
                                    });
                            })
                    )
                    .toList();

                CompletableFuture.allOf(migrationTasks.toArray(new CompletableFuture[0]))
                    .thenRun(() -> {
                        event.getHook().editOriginal("Migration complete! Migrated " + mojangProfiles.size() + " users.").queue();
                    })
                    .exceptionally(throwable -> {
                        log.error("Error during username migration", throwable);
                        event.getHook().editOriginal("Migration failed: " + throwable.getMessage()).queue();
                        return null;
                    });
            })
            .onError(throwable -> {
                log.error("Failed to load guild members for migration", throwable);
                event.getHook().editOriginal("Failed to load guild members").queue();
            });
    }

    @JDASlashCommand(name = "debug", subcommand = "role-restricted-activity", description = "Debug role-restricted channel activity for a user", defaultLocked = true)
    public void debugRoleRestrictedActivity(GuildSlashEvent event, @AppOption(description = "The user to debug") Member member) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());
        DiscordUser targetUser = discordUserRepository.findById(member.getId());

        if (discordUser == null || targetUser == null) {
            TranslationManager.edit(event.getHook(), "generic.user_not_found");
            return;
        }

        List<RoleRestrictedChannelGroup> groups = NerdBotApp.getBot().getConfig().getChannelConfig().getRoleRestrictedChannelGroups();

        if (groups.isEmpty()) {
            event.getHook().editOriginal("No role-restricted channel groups configured.").queue();
            return;
        }

        EmbedBuilder embedBuilder = new EmbedBuilder()
            .setTitle("Role-Restricted Channel Activity Debug")
            .setDescription("Activity data for " + member.getAsMention())
            .setColor(Color.ORANGE)
            .setThumbnail(member.getEffectiveAvatarUrl());

        LastActivity lastActivity = targetUser.getLastActivity();

        for (RoleRestrictedChannelGroup group : groups) {
            boolean hasAccess = Arrays.stream(group.getRequiredRoleIds())
                .anyMatch(roleId -> member.getRoles().stream()
                    .map(Role::getId)
                    .anyMatch(memberRoleId -> memberRoleId.equalsIgnoreCase(roleId)));

            StringBuilder fieldValue = new StringBuilder();
            fieldValue.append("**Has Access:** ").append(hasAccess ? "✅" : "❌").append("\n");

            if (hasAccess) {
                fieldValue.append("**Last Activity:** ").append(lastActivity.getRoleRestrictedChannelRelativeTimestamp(group.getIdentifier())).append("\n");
                fieldValue.append("**30-day Activity:**\n");
                fieldValue.append(" - Messages: ").append(lastActivity.getRoleRestrictedChannelMessageCount(group.getIdentifier(), 30)).append("/").append(group.getMinimumMessagesForActivity()).append("\n");
                fieldValue.append(" - Votes: ").append(lastActivity.getRoleRestrictedChannelVoteCount(group.getIdentifier(), 30)).append("/").append(group.getMinimumVotesForActivity()).append("\n");
                fieldValue.append(" - Comments: ").append(lastActivity.getRoleRestrictedChannelCommentCount(group.getIdentifier(), 30)).append("/").append(group.getMinimumCommentsForActivity()).append("\n");

                int messagesMet = lastActivity.getRoleRestrictedChannelMessageCount(group.getIdentifier(), group.getActivityCheckDays()) >= group.getMinimumMessagesForActivity() ? 1 : 0;
                int votesMet = lastActivity.getRoleRestrictedChannelVoteCount(group.getIdentifier(), group.getActivityCheckDays()) >= group.getMinimumVotesForActivity() ? 1 : 0;
                int commentsMet = lastActivity.getRoleRestrictedChannelCommentCount(group.getIdentifier(), group.getActivityCheckDays()) >= group.getMinimumCommentsForActivity() ? 1 : 0;
                int totalMet = messagesMet + votesMet + commentsMet;

                fieldValue.append("**Requirements Met:** ").append(totalMet).append("/3 ");
                if (totalMet >= 2) {
                    fieldValue.append("✅");
                } else {
                    fieldValue.append("⚠️");
                }
            } else {
                fieldValue.append("**Required Roles:** ");
                for (String roleId : group.getRequiredRoleIds()) {
                    Role role = member.getGuild().getRoleById(roleId);
                    if (role != null) {
                        fieldValue.append(role.getName()).append(" ");
                    }
                }
            }

            embedBuilder.addField(group.getDisplayName(), fieldValue.toString(), false);
        }

        event.getHook().editOriginalEmbeds(embedBuilder.build()).queue();
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
        return DiscordUtils.getMainGuild().getForumChannels();
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

        LoggingUtils.setGlobalLogLevel(logLevel);
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

    @JDASlashCommand(name = "force", subcommand = "inactivity-check", description = "Forcefully run the inactivity check", defaultLocked = true)
    public void forceInactiveCheck(GuildSlashEvent event) {
        UserNominationFeature.findInactiveUsers();
        TranslationManager.reply(event, "commands.force_inactivity_check.success");
    }

    @JDASlashCommand(name = "force", subcommand = "restricted-inactivity-check", description = "Forcefully run the restricted inactivity check", defaultLocked = true)
    public void forceRestrictedInactiveCheck(GuildSlashEvent event) {
        UserNominationFeature.findInactiveUsersInRoleRestrictedChannels();
        TranslationManager.reply(event, "commands.force_restricted_inactivity_check.success");
    }

    @JDASlashCommand(name = "reset-inactivity-check-data", description = "Reset the inactivity check data", defaultLocked = true)
    public void resetInactiveCheckData(GuildSlashEvent event) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        discordUserRepository.forEach(discordUser -> {
            LastActivity lastActivity = discordUser.getLastActivity();
            NominationInfo nominationInfo = lastActivity.getNominationInfo();

            nominationInfo.setTotalInactivityWarnings(0);
            nominationInfo.setLastInactivityWarningDate(null);
        });
    }

    @JDASlashCommand(name = "scan-channel", description = "Manually scan a channel for role membership", defaultLocked = true)
    public void scanChannelForRoleRestricted(GuildSlashEvent event, @AppOption(description = "The channel to scan") GuildChannel channel) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            TranslationManager.edit(event.getHook(), "generic.user_not_found");
            return;
        }

        BotConfig botConfig = NerdBotApp.getBot().getConfig();

        if (!botConfig.getChannelConfig().isAutoManageRoleRestrictedChannels()) {
            event.getHook().editOriginal("Automatic channel management is disabled. Enable it first with `/channel-config toggle`").queue();
            return;
        }

        List<String> currentGroups = new ArrayList<>();
        for (RoleRestrictedChannelGroup group : botConfig.getChannelConfig().getRoleRestrictedChannelGroups()) {
            if (Arrays.asList(group.getChannelIds()).contains(channel.getId())) {
                currentGroups.add(group.getDisplayName());
            }
        }

        RoleRestrictedChannelListener listener = new RoleRestrictedChannelListener();
        listener.updateChannelGroupMembership(channel, false);
        listener.updateChannelGroupMembership(channel, true);

        BotConfig updatedConfig = NerdBotApp.getBot().getConfig();
        List<String> newGroups = new ArrayList<>();
        for (RoleRestrictedChannelGroup group : updatedConfig.getChannelConfig().getRoleRestrictedChannelGroups()) {
            if (Arrays.asList(group.getChannelIds()).contains(channel.getId())) {
                newGroups.add(group.getDisplayName());
            }
        }

        EmbedBuilder embedBuilder = new EmbedBuilder()
            .setTitle("Channel Scan Results")
            .setDescription("Scanned " + channel.getAsMention() + " for role-restricted group membership")
            .setColor(Color.BLUE)
            .addField("Channel Type", channel.getType().name(), true)
            .addField("Before Scan",
                currentGroups.isEmpty() ? "No groups" : String.join(", ", currentGroups),
                false)
            .addField("After Scan",
                newGroups.isEmpty() ? "No groups" : String.join(", ", newGroups),
                false);

        StringBuilder permissionInfo = new StringBuilder();
        PermissionOverride everyoneOverride = channel.getPermissionContainer().getPermissionOverride(channel.getGuild().getPublicRole());
        if (everyoneOverride != null && everyoneOverride.getDenied().contains(Permission.VIEW_CHANNEL)) {
            permissionInfo.append("🔒 @everyone denied: `VIEW_CHANNEL`\n");

            List<String> allowedRoles = new ArrayList<>();
            for (PermissionOverride override : channel.getPermissionContainer().getPermissionOverrides()) {
                if (override.isRoleOverride() && override.getAllowed().contains(Permission.VIEW_CHANNEL)) {
                    allowedRoles.add(override.getRole().getName());
                }
            }

            if (!allowedRoles.isEmpty()) {
                permissionInfo.append("✅ Allowed roles: ").append(String.join(", ", allowedRoles));
            } else {
                permissionInfo.append("❌ No roles explicitly allowed");
            }
        } else {
            permissionInfo.append("🌐 Publicly accessible");
        }

        embedBuilder.addField("Permission Analysis", permissionInfo.toString(), false);

        event.getHook().editOriginalEmbeds(embedBuilder.build()).queue();

        log.info("{} manually scanned channel {} ({})",
            event.getUser().getName(), channel.getName(), channel.getId());
    }

    @JDASlashCommand(name = "channel-config", subcommand = "toggle", description = "Toggle automatic management of role-restricted channel groups", defaultLocked = true)
    public void toggleAutoManageRoleRestricted(GuildSlashEvent event) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            TranslationManager.reply(event, "generic.user_not_found");
            return;
        }

        BotConfig botConfig = NerdBotApp.getBot().getConfig();
        boolean currentState = botConfig.getChannelConfig().isAutoManageRoleRestrictedChannels();
        botConfig.getChannelConfig().setAutoManageRoleRestrictedChannels(!currentState);

        NerdBotApp.getBot().writeConfig(botConfig);

        String status = !currentState ? "enabled" : "disabled";
        event.reply("✅ Automatic management of role-restricted channel groups has been **" + status + "**.").setEphemeral(true).queue();

        log.info("{} {} automatic management of role-restricted channel groups",
            event.getUser().getName(), !currentState ? "enabled" : "disabled");
    }

    @JDASlashCommand(name = "channel-config", subcommand = "status", description = "View the status of automatic role-restricted channel management", defaultLocked = true)
    public void autoManageStatus(GuildSlashEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            TranslationManager.edit(event.getHook(), "generic.user_not_found");
            return;
        }

        BotConfig botConfig = NerdBotApp.getBot().getConfig();
        ChannelConfig channelConfig = botConfig.getChannelConfig();

        EmbedBuilder embedBuilder = new EmbedBuilder()
            .setTitle("Role-Restricted Channel Auto-Management Status")
            .setColor(channelConfig.isAutoManageRoleRestrictedChannels() ? Color.GREEN : Color.RED)
            .addField("Automatic Management",
                channelConfig.isAutoManageRoleRestrictedChannels() ? "✅ Enabled" : "❌ Disabled",
                false)
            .addField("Configured Groups",
                String.valueOf(channelConfig.getRoleRestrictedChannelGroups().size()),
                true);

        if (channelConfig.isAutoManageRoleRestrictedChannels()) {
            embedBuilder
                .addField("Default Message Requirement",
                    String.valueOf(channelConfig.getDefaultMinimumMessagesForActivity()),
                    true)
                .addField("Default Vote Requirement",
                    String.valueOf(channelConfig.getDefaultMinimumVotesForActivity()),
                    true)
                .addField("Default Comment Requirement",
                    String.valueOf(channelConfig.getDefaultMinimumCommentsForActivity()),
                    true)
                .addField("Default Check Period",
                    channelConfig.getDefaultActivityCheckDays() + " days",
                    true)
                .addBlankField(true);
        }

        int totalChannels = channelConfig.getRoleRestrictedChannelGroups().stream()
            .mapToInt(group -> group.getChannelIds().length)
            .sum();

        embedBuilder.addField("Total Tracked Channels", String.valueOf(totalChannels), false);

        event.getHook().editOriginalEmbeds(embedBuilder.build()).queue();
    }

    @JDASlashCommand(name = "channel-config", subcommand = "rebuild", description = "Rebuild role-restricted channel groups from current permissions", defaultLocked = true)
    public void rebuildRoleRestrictedGroups(GuildSlashEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            TranslationManager.edit(event.getHook(), "generic.user_not_found");
            return;
        }

        BotConfig botConfig = NerdBotApp.getBot().getConfig();

        if (!botConfig.getChannelConfig().isAutoManageRoleRestrictedChannels()) {
            event.getHook().editOriginal("❌ Automatic channel management is disabled. Enable it first with `/channel-config toggle`").queue();
            return;
        }

        int groupsBefore = botConfig.getChannelConfig().getRoleRestrictedChannelGroups().size();
        int channelsBefore = botConfig.getChannelConfig().getRoleRestrictedChannelGroups().stream()
            .mapToInt(group -> group.getChannelIds().length)
            .sum();

        event.getHook().editOriginal("🔄 Scanning all guild channels and rebuilding groups... This may take a moment.").queue();

        NerdBotApp.EXECUTOR_SERVICE.execute(() -> {
            try {
                botConfig.getChannelConfig().rebuildRoleRestrictedChannelGroups();
                NerdBotApp.getBot().writeConfig(botConfig);

                BotConfig finalConfig = NerdBotApp.getBot().getConfig();
                int groupsAfter = finalConfig.getChannelConfig().getRoleRestrictedChannelGroups().size();
                int channelsAfter = finalConfig.getChannelConfig().getRoleRestrictedChannelGroups().stream()
                    .mapToInt(group -> group.getChannelIds().length)
                    .sum();

                StringBuilder summary = new StringBuilder("✅ **Rebuild Complete**\n\n");
                summary.append(String.format("**Before:** %d groups, %d channels\n", groupsBefore, channelsBefore));
                summary.append(String.format("**After:** %d groups, %d channels\n\n", groupsAfter, channelsAfter));

                if (groupsAfter > 0) {
                    summary.append("**Created Groups:**\n");
                    for (RoleRestrictedChannelGroup group : finalConfig.getChannelConfig().getRoleRestrictedChannelGroups()) {
                        summary.append(String.format("- **%s** (%d channels)\n",
                            group.getDisplayName(), group.getChannelIds().length));
                    }
                } else {
                    summary.append("**Result:** No role-restricted channels found - all channels appear to be publicly accessible.");
                }

                event.getHook().editOriginal(summary.toString()).queue();

                log.info("Role-restricted channel groups rebuild completed by {}: {} groups -> {} groups, {} channels -> {} channels",
                    event.getUser().getName(), groupsBefore, groupsAfter, channelsBefore, channelsAfter);

            } catch (Exception e) {
                log.error("Error during role-restricted channel groups rebuild", e);
                event.getHook().editOriginal("❌ **Rebuild Failed**\n\nAn error occurred during the rebuild process. Check the logs for details.").queue();
            }
        });
    }

    @JDASlashCommand(name = "channel-config", subcommand = "clean", description = "Remove empty role-restricted channel groups", defaultLocked = true)
    public void cleanRoleRestrictedGroups(GuildSlashEvent event) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            TranslationManager.reply(event, "generic.user_not_found");
            return;
        }

        BotConfig botConfig = NerdBotApp.getBot().getConfig();
        int groupsBefore = botConfig.getChannelConfig().getRoleRestrictedChannelGroups().size();

        boolean removed = botConfig.getChannelConfig().removeEmptyRoleRestrictedGroups();

        if (removed) {
            NerdBotApp.getBot().writeConfig(botConfig);
            int groupsAfter = botConfig.getChannelConfig().getRoleRestrictedChannelGroups().size();
            int removedCount = groupsBefore - groupsAfter;

            event.reply(String.format("✅ Removed %d empty role-restricted channel group(s). %d groups remaining.",
                removedCount, groupsAfter)).setEphemeral(true).queue();

            log.info("{} cleaned role-restricted channel groups: removed {} empty groups",
                event.getUser().getName(), removedCount);
        } else {
            event.reply("✅ No empty role-restricted channel groups found.").setEphemeral(true).queue();
        }
    }

    @JDASlashCommand(name = "debug", subcommand = "ungrouped-channels", description = "Show channels that are not part of any role-restricted group", defaultLocked = true)
    public void debugUngroupedChannels(GuildSlashEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            TranslationManager.edit(event.getHook(), "generic.user_not_found");
            return;
        }

        BotConfig botConfig = NerdBotApp.getBot().getConfig();
        List<RoleRestrictedChannelGroup> groups = botConfig.getChannelConfig().getRoleRestrictedChannelGroups();
        Set<String> groupedChannelIds = new HashSet<>();

        for (RoleRestrictedChannelGroup group : groups) {
            groupedChannelIds.addAll(Arrays.asList(group.getChannelIds()));
        }

        List<GuildChannel> ungroupedTextChannels = new ArrayList<>();
        List<GuildChannel> ungroupedVoiceChannels = new ArrayList<>();
        List<GuildChannel> ungroupedForumChannels = new ArrayList<>();
        List<GuildChannel> ungroupedOtherChannels = new ArrayList<>();

        Guild guild = event.getGuild();

        for (TextChannel channel : guild.getTextChannels()) {
            if (!groupedChannelIds.contains(channel.getId())) {
                ungroupedTextChannels.add(channel);
            }
        }

        for (VoiceChannel channel : guild.getVoiceChannels()) {
            if (!groupedChannelIds.contains(channel.getId())) {
                ungroupedVoiceChannels.add(channel);
            }
        }

        for (ForumChannel channel : guild.getForumChannels()) {
            if (!groupedChannelIds.contains(channel.getId())) {
                ungroupedForumChannels.add(channel);
            }
        }

        for (GuildChannel channel : guild.getChannels()) {
            if (!groupedChannelIds.contains(channel.getId()) &&
                channel.getType() != ChannelType.TEXT &&
                channel.getType() != ChannelType.VOICE &&
                channel.getType() != ChannelType.FORUM) {
                ungroupedOtherChannels.add(channel);
            }
        }

        int totalUngrouped = ungroupedTextChannels.size() + ungroupedVoiceChannels.size()
            + ungroupedForumChannels.size() + ungroupedOtherChannels.size();
        int totalChannels = guild.getChannels().size();
        int totalGrouped = groupedChannelIds.size();

        EmbedBuilder embedBuilder = new EmbedBuilder()
            .setTitle("📋 Ungrouped Channels Analysis")
            .setColor(totalUngrouped > 0 ? Color.YELLOW : Color.GREEN)
            .setDescription("Channels that are not part of any role-restricted channel group")
            .addField("📊 Summary",
                String.format("**Total Channels:** %d\n**Grouped:** %d\n**Ungrouped:** %d\n**Groups:** %d",
                    totalChannels, totalGrouped, totalUngrouped, groups.size()),
                false);

        if (!ungroupedTextChannels.isEmpty()) {
            StringBuilder textChannels = new StringBuilder();
            for (int i = 0; i < Math.min(ungroupedTextChannels.size(), 10); i++) {
                GuildChannel channel = ungroupedTextChannels.get(i);
                textChannels.append("- ").append(channel.getAsMention())
                    .append(" (").append(getChannelAccessType(channel)).append(")\n");
            }
            if (ungroupedTextChannels.size() > 10) {
                textChannels.append("... and ").append(ungroupedTextChannels.size() - 10).append(" more");
            }

            embedBuilder.addField(String.format("💬 Text Channels (%d)", ungroupedTextChannels.size()),
                textChannels.toString(), false);
        }

        if (!ungroupedVoiceChannels.isEmpty()) {
            StringBuilder voiceChannels = new StringBuilder();
            for (int i = 0; i < Math.min(ungroupedVoiceChannels.size(), 10); i++) {
                GuildChannel channel = ungroupedVoiceChannels.get(i);
                voiceChannels.append("- ").append(channel.getName())
                    .append(" (").append(getChannelAccessType(channel)).append(")\n");
            }
            if (ungroupedVoiceChannels.size() > 10) {
                voiceChannels.append("... and ").append(ungroupedVoiceChannels.size() - 10).append(" more");
            }

            embedBuilder.addField(String.format("🔊 Voice Channels (%d)", ungroupedVoiceChannels.size()),
                voiceChannels.toString(), false);
        }

        if (!ungroupedForumChannels.isEmpty()) {
            StringBuilder forumChannels = new StringBuilder();
            for (int i = 0; i < Math.min(ungroupedForumChannels.size(), 10); i++) {
                GuildChannel channel = ungroupedForumChannels.get(i);
                forumChannels.append(" - ").append(channel.getAsMention())
                    .append(" (").append(getChannelAccessType(channel)).append(")\n");
            }
            if (ungroupedForumChannels.size() > 10) {
                forumChannels.append("... and ").append(ungroupedForumChannels.size() - 10).append(" more");
            }

            embedBuilder.addField(String.format("📋 Forum Channels (%d)", ungroupedForumChannels.size()),
                forumChannels.toString(), false);
        }

        if (!ungroupedOtherChannels.isEmpty()) {
            StringBuilder otherChannels = new StringBuilder();
            for (int i = 0; i < Math.min(ungroupedOtherChannels.size(), 5); i++) {
                GuildChannel channel = ungroupedOtherChannels.get(i);
                otherChannels.append(" - ").append(channel.getName())
                    .append(" (").append(channel.getType().name()).append(")\n");
            }
            if (ungroupedOtherChannels.size() > 5) {
                otherChannels.append("... and ").append(ungroupedOtherChannels.size() - 5).append(" more");
            }

            embedBuilder.addField(String.format("🔧 Other Channels (%d)", ungroupedOtherChannels.size()),
                otherChannels.toString(), false);
        }
        
        embedBuilder.setTimestamp(Instant.now());
        event.getHook().editOriginalEmbeds(embedBuilder.build()).queue();
    }

    /**
     * Helper method to determine channel access type
     */
    private String getChannelAccessType(GuildChannel channel) {
        PermissionOverride everyoneOverride = channel.getPermissionContainer().getPermissionOverride(channel.getGuild().getPublicRole());

        if (everyoneOverride != null && everyoneOverride.getDenied().contains(Permission.VIEW_CHANNEL)) {
            List<String> allowedRoles = new ArrayList<>();
            for (PermissionOverride override : channel.getPermissionContainer().getPermissionOverrides()) {
                if (override.isRoleOverride() && override.getAllowed().contains(Permission.VIEW_CHANNEL)) {
                    allowedRoles.add(override.getRole().getName());
                }
            }

            if (allowedRoles.isEmpty()) {
                return "Private - No roles allowed";
            } else if (allowedRoles.size() == 1) {
                return "Role-restricted: " + allowedRoles.get(0);
            } else {
                return "Role-restricted: " + allowedRoles.size() + " roles";
            }
        } else {
            return "Public";
        }
    }
}
