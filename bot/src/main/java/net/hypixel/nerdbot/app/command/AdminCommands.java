package net.hypixel.nerdbot.app.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashAutocompleteHandler;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashComponentHandler;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
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
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.restaction.InviteAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.app.curator.ForumChannelCurator;
import net.hypixel.nerdbot.app.listener.RoleRestrictedChannelListener;
import net.hypixel.nerdbot.app.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.app.nomination.InactivitySweepReport;
import net.hypixel.nerdbot.app.nomination.NominationInactivityService;
import net.hypixel.nerdbot.app.nomination.NominationService;
import net.hypixel.nerdbot.app.nomination.NominationSweepReport;
import net.hypixel.nerdbot.app.util.HttpUtils;
import net.hypixel.nerdbot.core.FileUtils;
import net.hypixel.nerdbot.core.JsonUtils;
import net.hypixel.nerdbot.core.TimeUtils;
import net.hypixel.nerdbot.core.exception.RepositoryException;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.api.bot.Bot;
import net.hypixel.nerdbot.discord.api.bot.DiscordBot;
import net.hypixel.nerdbot.discord.api.bot.Environment;
import net.hypixel.nerdbot.discord.cache.ChannelCache;
import net.hypixel.nerdbot.discord.config.MetricsConfig;
import net.hypixel.nerdbot.discord.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.config.channel.ChannelConfig;
import net.hypixel.nerdbot.discord.config.objects.RoleRestrictedChannelGroup;
import net.hypixel.nerdbot.discord.storage.curator.Curator;
import net.hypixel.nerdbot.discord.storage.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.discord.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.discord.storage.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.discord.storage.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.discord.storage.database.model.user.stats.NominationInfo;
import net.hypixel.nerdbot.discord.storage.database.repository.DiscordUserRepository;
import net.hypixel.nerdbot.discord.storage.repository.Repository;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import net.hypixel.nerdbot.discord.util.Utils;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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

@Slf4j
public class AdminCommands {

    @SlashCommand(name = "curate", description = "Manually run the curation process", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void curate(SlashCommandInteractionEvent event, @SlashOption ForumChannel channel, @SlashOption(description = "Run the curator without greenlighting suggestions", required = false) Boolean readOnly) {
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        final boolean finalReadOnly = readOnly != null && readOnly;

        discordUserRepository.findByIdAsync(event.getMember().getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    event.reply("User not found").queue();
                    return;
                }

                if (!BotEnvironment.getBot().getDatabase().isConnected()) {
                    event.reply("Could not connect to database!").queue();
                    log.error("Couldn't connect to the database!");
                    return;
                }

                Curator<ForumChannel, ThreadChannel> forumChannelCurator = new ForumChannelCurator(finalReadOnly);

                BotEnvironment.EXECUTOR_SERVICE.execute(() -> {
                    event.deferReply(true).complete();

                    TimerTask task = new TimerTask() {
                        @Override
                        public void run() {
                            BotEnvironment.EXECUTOR_SERVICE.execute(() -> {
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
                        event.getHook().editOriginal("No suggestions were greenlit").queue();
                    } else {
                        event.getHook().editOriginal(String.format("Greenlit %d suggestions in %dms!", output.size(), forumChannelCurator.getEndTime() - forumChannelCurator.getStartTime())).queue();
                    }
                });
            })
            .exceptionally(throwable -> {
                log.error("Error loading user for curation", throwable);
                event.reply("Failed to load user data").queue();
                return null;
            });
    }

    @SlashCommand(name = "invites", subcommand = "create", description = "Generate a bunch of invites for a specific channel.", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void createInvites(SlashCommandInteractionEvent event, @SlashOption int amount, @SlashOption(required = false) TextChannel channel) {
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        discordUserRepository.findByIdAsync(event.getMember().getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    event.reply("User not found").queue();
                    return;
                }

                List<Invite> invites = new ArrayList<>(amount);
                TextChannel selected = Objects.requireNonNullElse(channel, DiscordBotEnvironment.getBot().getJDA().getTextChannelsByName("limbo", true).get(0));
                event.deferReply(true).complete();

                ChannelCache.sendToLogChannel(
                    new EmbedBuilder()
                        .setTitle("Invites Created")
                        .setDescription(event.getUser().getAsMention() + " created " + amount + " invite(s) for " + selected.getAsMention() + ".")
                        .build()
                );

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
                        event.getHook().editOriginal(String.format("I do not have the correct permission to create invites in %s!", selected.getAsMention())).queue();
                        return;
                    }
                }

                StringBuilder stringBuilder = new StringBuilder("**" + String.format("Created %d invites:", invites.size()) + "**\n");
                invites.forEach(invite -> stringBuilder.append(invite.getUrl()).append("\n"));
                event.getHook().editOriginal(stringBuilder.toString()).queue();
            })
            .exceptionally(throwable -> {
                log.error("Error loading user for invite creation", throwable);
                event.reply("Failed to load user data").queue();
                return null;
            });
    }

    @SlashCommand(name = "invites", subcommand = "delete", description = "Delete all active invites.", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void deleteInvites(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        discordUserRepository.findOrCreateByIdAsync(event.getMember().getId())
            .thenAccept(discordUser -> {
                List<Invite> invites = event.getGuild().retrieveInvites().complete();
                invites.forEach(invite -> {
                    invite.delete().complete();
                    log.info(event.getUser().getName() + " deleted invite " + invite.getUrl());
                });

                ChannelCache.sendToLogChannel(
                    new EmbedBuilder()
                        .setTitle("Invites Deleted")
                        .setDescription(event.getUser().getAsMention() + " deleted all " + invites.size() + " invite(s).")
                        .build()
                );

                event.getHook().editOriginal(String.format("Deleted %d invites!", invites.size())).queue();
            })
            .exceptionally(throwable -> {
                log.error("Error loading user for invite deletion", throwable);
                event.getHook().editOriginal("Failed to load user data").queue();
                return null;
            });
    }

    @SlashCommand(name = "config", subcommand = "show", description = "View the currently loaded config", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void showConfig(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            event.getHook().editOriginal("User not found").queue();
            return;
        }

        try {
            File file = FileUtils.createTempFile("config-" + System.currentTimeMillis() + ".json", BotEnvironment.GSON.toJson(DiscordBotEnvironment.getBot().getConfig()));
            event.getHook().editOriginalAttachments(FileUpload.fromData(file)).queue();
        } catch (IOException exception) {
            event.getHook().editOriginal("An error occurred while reading the config file! Please check the logs for more information.").queue();
            log.error("An error occurred when reading the JSON file!", exception);
        }
    }

    @SlashCommand(name = "config", subcommand = "reload", description = "Reload the config file", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void reloadConfig(SlashCommandInteractionEvent event) {
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        discordUserRepository.findByIdAsync(event.getMember().getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    event.reply("User not found").queue();
                    return;
                }

                event.deferReply().setEphemeral(true).complete();

                DiscordBot bot = DiscordBotEnvironment.getBot();
                bot.loadConfig();
                bot.getJDA().getPresence().setActivity(Activity.of(Activity.ActivityType.valueOf(bot.getConfig().getActivityType().name()), bot.getConfig().getActivity()));
                PrometheusMetrics.setMetricsEnabled(SkyBlockNerdsBot.config().getMetricsConfig().isEnabled());

                event.getHook().editOriginal("Reloaded the config file!").queue();
            })
            .exceptionally(throwable -> {
                log.error("Error loading user for config reload", throwable);
                event.reply("Failed to load user data").queue();
                return null;
            });
    }

    @SlashCommand(name = "config", subcommand = "edit", description = "Edit the config file", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void editConfig(SlashCommandInteractionEvent event, @SlashOption String key, @SlashOption String value) {
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            event.reply("User not found").queue();
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

                return JsonUtils.writeJsonFileAsync(fileName, JsonUtils.setJsonValue(obj, key, element));
            })
            .thenRun(() -> {
                log.info(event.getUser().getName() + " edited the config file!");
                event.reply("Updated config file!").queue();
            })
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof JsonSyntaxException) {
                    event.reply(String.format("Could not find given key `%s` in the config file!", throwable.getCause().getMessage())).queue();
                } else {
                    log.error("Error processing config file", throwable);
                    event.reply("An error occurred while reading the config file! Please check the logs for more information.").queue();
                }
                return null;
            });
    }

    @SlashCommand(name = "metrics", subcommand = "toggle", description = "Toggle metrics collection", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void toggleMetrics(SlashCommandInteractionEvent event) {
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            event.reply("User not found").queue();
            return;
        }

        Bot bot = BotEnvironment.getBot();
        MetricsConfig metricsConfig = SkyBlockNerdsBot.config().getMetricsConfig();
        metricsConfig.setEnabled(!metricsConfig.isEnabled());
        PrometheusMetrics.setMetricsEnabled(metricsConfig.isEnabled());

        event.reply(String.format("Toggled metrics: %s", metricsConfig.isEnabled())).queue();
    }

    @SlashCommand(
        name = "user",
        subcommand = "link",
        description = "Link a Mojang Profile to a member's account.",
        guildOnly = true,
        defaultMemberPermissions = {"BAN_MEMBERS"}
    )
    public void linkProfile(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Member to link to.") Member member,
        @SlashOption(description = "Your Minecraft IGN to link.") String username,
        @SlashOption(description = "Bypass hypixel social check.", required = false) Boolean bypassSocial
    ) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        discordUserRepository.findByIdAsync(member.getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    event.reply("User not found").queue();
                    return;
                }

                boolean enforceSocial = bypassSocial == null || !bypassSocial;

                ProfileCommands.requestMojangProfileAsync(member, username, enforceSocial)
                    .thenAccept(mojangProfile -> {
                        if (mojangProfile == null) {
                            event.getHook().editOriginal(String.format("Could not find player with username `%s`! (%s)", username, "Profile not found")).queue();
                            return;
                        }

                        if (mojangProfile.getErrorMessage() != null) {
                            if (mojangProfile.getErrorMessage().contains("does not match")) {
                                event.getHook().editOriginal(String.format("Profile mismatch error for %s: %s", username, mojangProfile.getErrorMessage())).queue();
                            } else {
                                event.getHook().editOriginal(mojangProfile.getErrorMessage()).queue();
                            }
                            return;
                        }

                        ProfileCommands.updateMojangProfile(member, mojangProfile);
                        event.getHook().editOriginal(String.format("Updated profile for %s to %s (UUID: %s)", member.getAsMention(), mojangProfile.getUsername(), mojangProfile.getUniqueId())).queue();

                        ChannelCache.sendToLogChannel(
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
                        );
                    })
                    .exceptionally(throwable -> {
                        log.error("Error during profile linking", throwable);
                        event.getHook().editOriginal(String.format("Could not find player with username `%s`! (%s)", username, throwable.getMessage())).queue();
                        return null;
                    });
            })
            .exceptionally(throwable -> {
                log.error("Error loading user for profile linking", throwable);
                event.getHook().editOriginal("Failed to load user data").queue();
                return null;
            });
    }

    @SlashCommand(
        name = "user",
        subcommand = "missing",
        description = "List any user with no assigned Mojang Profile.",
        guildOnly = true,
        defaultMemberPermissions = {"BAN_MEMBERS"}
    )
    public void userMissingProfile(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

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

    @SlashCommand(
        name = "user",
        subcommand = "info",
        description = "View information about a user",
        guildOnly = true,
        defaultMemberPermissions = {"BAN_MEMBERS"}
    )
    public void userInfo(SlashCommandInteractionEvent event, @SlashOption(description = "The user to search") Member member) {
        event.deferReply(true).queue();
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
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
            .addField("Birthday", discordUser.getBirthdayData().getFormattedDisplay(), false)
            .build());
        embeds.add(1, ProfileCommands.createBadgesEmbed(member, discordUser, false));

        event.getHook().editOriginalEmbeds(embeds.toArray(new MessageEmbed[]{})).queue();
    }

    @SlashCommand(
        name = "user",
        subcommand = "badges",
        description = "View the badges of a user",
        guildOnly = true,
        defaultMemberPermissions = {"BAN_MEMBERS"}
    )
    public void viewUserBadges(SlashCommandInteractionEvent event, @SlashOption(description = "The user to search") Member member) {
        event.deferReply(true).queue();
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(member.getId());

        if (discordUser == null) {
            event.getHook().editOriginal("User not found").queue();
            return;
        }

        event.getHook().editOriginalEmbeds(ProfileCommands.createBadgesEmbed(member, discordUser, event.getUser().getId().equals(member.getId()))).queue();
    }

    @SlashCommand(
        name = "user",
        group = "migrate",
        subcommand = "names",
        description = "Attempts to migrate any user with no assigned Mojang Profile using their display name.",
        guildOnly = true,
        defaultMemberPermissions = {"ADMINISTRATOR"}
    )
    public void migrateUsernames(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            event.getHook().editOriginal("User not found").queue();
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
                                return HttpUtils.getMojangProfileAsync(scuffedUsername)
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

    @SlashCommand(name = "debug", subcommand = "role-restricted-activity", description = "Debug role-restricted channel activity for a user", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void debugRoleRestrictedActivity(SlashCommandInteractionEvent event, @SlashOption(description = "The user to debug") Member member) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());
        DiscordUser targetUser = discordUserRepository.findById(member.getId());

        if (discordUser == null || targetUser == null) {
            event.getHook().editOriginal("User not found").queue();
            return;
        }

        List<RoleRestrictedChannelGroup> groups = SkyBlockNerdsBot.config().getChannelConfig().getRoleRestrictedChannelGroups();

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

    @SlashCommand(name = "cache", subcommand = "force-save", description = "Force save the specified cache to the database", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void forceSaveRepository(SlashCommandInteractionEvent event, @SlashOption String repositoryName) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            event.getHook().editOriginal("User not found").queue();
            return;
        }

        try {
            Repository<?> repository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(repositoryName);
            if (repository == null) {
                event.getHook().editOriginal("Could not find a repository with that name!").queue();
                return;
            }

            repository.saveAllToDatabase();
            event.getHook().editOriginal(String.format("Saved %d documents to the database!", repository.getCache().estimatedSize())).queue();
        } catch (RepositoryException exception) {
            event.getHook().editOriginal(String.format("An error occurred while saving the repository: %s", exception.getMessage())).queue();
            log.error("An error occurred while saving the repository!", exception);
        }
    }

    @SlashCommand(name = "cache", subcommand = "force-load", description = "Forcefully load documents from the database into the cache", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void forceLoadDocuments(SlashCommandInteractionEvent event, @SlashOption String repositoryName) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            event.getHook().editOriginal("User not found").queue();
            return;
        }

        try {
            Repository<?> repository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(repositoryName);

            if (repository == null) {
                event.getHook().editOriginal("Could not find a repository with that name!").queue();
                return;
            }

            repository.loadAllDocumentsIntoCache();
            event.getHook().editOriginal(String.format("Loaded %d documents from the database!", repository.getCache().estimatedSize())).queue();
        } catch (RepositoryException exception) {
            event.getHook().editOriginal(String.format("An error occurred while loading the repository: %s", exception.getMessage())).queue();
            log.error("An error occurred while saving the repository!", exception);
        }
    }

    @SlashCommand(name = "cache", subcommand = "stats", description = "View cache statistics", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void cacheStats(SlashCommandInteractionEvent event, @SlashOption String repositoryName) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findOrCreateById(event.getUser().getId());

        try {
            Repository<?> repository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(repositoryName);

            if (repository == null) {
                event.getHook().editOriginal("Could not find a repository with that name!").queue();
                return;
            }

            event.getHook().editOriginal(repository.getCache().stats().toString()).queue();
        } catch (RepositoryException exception) {
            event.getHook().editOriginal(String.format("An error occurred while getting the statistics for that repository: %s", exception.getMessage())).queue();
            log.error("An error occurred while saving the repository!", exception);
        }
    }

    @SlashCommand(name = "admin", subcommand = "transfer-tag", description = "Interactive forum tag transfer panel", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void transferForumTag(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();
        createTagTransferPanel(event.getHook(), event.getUser().getId());
    }


    @SlashComponentHandler(id = "tag-channel-select", patterns = {"tag-channel-select-*"})
    public void handleChannelSelect(StringSelectInteractionEvent event) {
        String userId = event.getComponentId().split("-")[3];
        if (!event.getUser().getId().equals(userId)) {
            event.reply("You can only use your own tag transferring interface!").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();
        String channelId = event.getValues().get(0);
        createTagSelectionStep(event.getHook(), userId, channelId);
    }

    @SlashComponentHandler(id = "tag-from-select", patterns = {"tag-from-select-*"})
    public void handleFromTagSelect(StringSelectInteractionEvent event) {
        String[] parts = event.getComponentId().split("-");
        String userId = parts[3];
        String channelId = parts[4];

        if (!event.getUser().getId().equals(userId)) {
            event.reply("You can only use your own tag transferring interface!").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();
        String fromTagId = event.getValues().get(0);
        createToTagSelectionStep(event.getHook(), userId, channelId, fromTagId);
    }

    @SlashComponentHandler(id = "tag-to-select", patterns = {"tag-to-select-*"})
    public void handleToTagSelect(StringSelectInteractionEvent event) {
        String[] parts = event.getComponentId().split("-");
        String userId = parts[3];
        String channelId = parts[4];
        String fromTagId = parts[5];

        if (!event.getUser().getId().equals(userId)) {
            event.reply("You can only use your own tag transferring interface!").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();
        String toTagId = event.getValues().get(0);
        createConfirmationStep(event.getHook(), userId, channelId, fromTagId, toTagId);
    }

    @SlashComponentHandler(id = "tag-confirm", patterns = {"tag-confirm-*"})
    public void handleTagConfirm(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split("-");
        String action = parts[2]; // "execute" or "cancel"
        String userId = parts[3];
        String channelId = parts[4];
        String fromTagId = parts[5];
        String toTagId = parts[6];

        if (!event.getUser().getId().equals(userId)) {
            event.reply("You can only use your own tag transferring interface!").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        if (action.equals("cancel")) {
            createTagTransferPanel(event.getHook(), userId, "❌ Tag transfer cancelled.");
            return;
        }

        executeTagTransfer(event.getHook(), userId, channelId, fromTagId, toTagId);
    }

    @SlashComponentHandler(id = "tag-back", patterns = {"tag-back-*"})
    public void handleTagBack(ButtonInteractionEvent event) {
        String userId = event.getComponentId().split("-")[2];
        if (!event.getUser().getId().equals(userId)) {
            event.reply("You can only use your own tag transferring interface!").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();
        createTagTransferPanel(event.getHook(), userId);
    }

    private void createTagTransferPanel(InteractionHook hook, String userId) {
        createTagTransferPanel(hook, userId, null);
    }

    private void createTagTransferPanel(InteractionHook hook, String userId, String message) {
        List<ForumChannel> forumChannels = DiscordUtils.getMainGuild().getForumChannels();

        if (forumChannels.isEmpty()) {
            hook.editOriginal("❌ No forum channels found in this server!").setComponents().queue();
            return;
        }

        StringBuilder content = new StringBuilder();
        content.append("🏷️ **Tag Transfer Panel**\n\n");

        if (message != null) {
            content.append(message).append("\n\n");
        }

        content.append("**Step 1:** Select the forum channel where you want to transfer tags.\n\n");
        content.append("📊 **Available Forum Channels:** ").append(forumChannels.size());

        StringSelectMenu.Builder channelSelect = StringSelectMenu.create("tag-channel-select-" + userId)
            .setPlaceholder("🏛️ Select Forum Channel")
            .setRequiredRange(1, 1);

        forumChannels.stream()
            .limit(25)
            .forEach(channel -> channelSelect.addOption(
                channel.getName(),
                channel.getId(),
                "Transfer tags in " + channel.getName()
            ));

        ActionRow selectRow = ActionRow.of(channelSelect.build());

        hook.editOriginal(content.toString())
            .setComponents(selectRow)
            .queue();
    }

    private void createTagSelectionStep(InteractionHook hook, String userId, String channelId) {
        ForumChannel channel = DiscordUtils.getMainGuild().getForumChannelById(channelId);
        if (channel == null) {
            createTagTransferPanel(hook, userId, "❌ Forum channel not found!");
            return;
        }

        List<ForumTag> tags = channel.getAvailableTags();
        if (tags.isEmpty()) {
            createTagTransferPanel(hook, userId, "❌ No tags found in " + channel.getName() + "!");
            return;
        }

        String content = "🏷️ **Tag Transfer Panel**\n\n" +
            "**Step 2:** Select the source tag to transfer FROM.\n\n" +
            "📍 **Selected Channel:** " + channel.getName() + "\n" +
            "🏷️ **Available Tags:** " + tags.size() + "\n\n";

        StringSelectMenu.Builder tagSelect = StringSelectMenu.create("tag-from-select-" + userId + "-" + channelId)
            .setPlaceholder("🏷️ Select Source Tag (FROM)")
            .setRequiredRange(1, 1);

        tags.stream()
            .limit(25)
            .forEach(tag -> tagSelect.addOption(
                tag.getName(),
                tag.getId(),
                "Transfer FROM this tag"
            ));

        ActionRow selectRow = ActionRow.of(tagSelect.build());
        ActionRow backRow = ActionRow.of(
            Button.secondary("tag-back-" + userId, "⬅️ Back to Channel Selection")
        );

        hook.editOriginal(content)
            .setComponents(selectRow, backRow)
            .queue();
    }

    private void createToTagSelectionStep(InteractionHook hook, String userId, String channelId, String fromTagId) {
        ForumChannel channel = DiscordUtils.getMainGuild().getForumChannelById(channelId);
        if (channel == null) {
            createTagTransferPanel(hook, userId, "❌ Forum channel not found!");
            return;
        }

        ForumTag fromTag = channel.getAvailableTagById(fromTagId);
        if (fromTag == null) {
            createTagSelectionStep(hook, userId, channelId);
            return;
        }

        List<ForumTag> tags = channel.getAvailableTags().stream()
            .filter(tag -> !tag.getId().equals(fromTagId))
            .toList();

        if (tags.isEmpty()) {
            createTagSelectionStep(hook, userId, channelId);
            return;
        }

        String content = "🏷️ **Tag Transfer Panel**\n\n" +
            "**Step 3:** Select the destination tag to transfer TO.\n\n" +
            "📍 **Channel:** " + channel.getName() + "\n" +
            "📤 **From Tag:** " + fromTag.getName() + "\n" +
            "🏷️ **Available Destination Tags:** " + tags.size() + "\n\n";

        StringSelectMenu.Builder tagSelect = StringSelectMenu.create("tag-to-select-" + userId + "-" + channelId + "-" + fromTagId)
            .setPlaceholder("🎯 Select Destination Tag (TO)")
            .setRequiredRange(1, 1);

        tags.stream()
            .limit(25)
            .forEach(tag -> tagSelect.addOption(
                tag.getName(),
                tag.getId(),
                "Transfer TO this tag"
            ));

        ActionRow selectRow = ActionRow.of(tagSelect.build());
        ActionRow backRow = ActionRow.of(
            Button.secondary("tag-back-" + userId, "⬅️ Back to Channel Selection")
        );

        hook.editOriginal(content)
            .setComponents(selectRow, backRow)
            .queue();
    }

    private void createConfirmationStep(InteractionHook hook, String userId, String channelId, String fromTagId, String toTagId) {
        ForumChannel channel = DiscordUtils.getMainGuild().getForumChannelById(channelId);
        ForumTag fromTag = channel != null ? channel.getAvailableTagById(fromTagId) : null;
        ForumTag toTag = channel != null ? channel.getAvailableTagById(toTagId) : null;

        if (channel == null || fromTag == null || toTag == null) {
            createTagTransferPanel(hook, userId, "❌ Invalid channel or tags!");
            return;
        }

        // Count affected threads
        List<ThreadChannel> affectedThreads = Stream.concat(
                channel.getThreadChannels().stream(),
                channel.retrieveArchivedPublicThreadChannels().stream()
            )
            .filter(thread -> thread.getAppliedTags().contains(fromTag))
            .distinct()
            .toList();

        StringBuilder content = new StringBuilder();
        content.append("🏷️ **Tag Transfer Confirmation**\n\n");
        content.append("⚠️ **Please confirm the following transfer:**\n\n");
        content.append("📍 **Channel:** ").append(channel.getName()).append("\n");
        content.append("📤 **From Tag:** `").append(fromTag.getName()).append("`\n");
        content.append("📥 **To Tag:** `").append(toTag.getName()).append("`\n");
        content.append("📊 **Affected Threads:** ").append(affectedThreads.size()).append("\n\n");

        if (affectedThreads.isEmpty()) {
            content.append("⚠️ **Warning:** No threads found with the source tag!\n\n");
        } else {
            content.append("🔄 **This will:**\n");
            content.append("• Remove `").append(fromTag.getName()).append("` from ").append(affectedThreads.size()).append(" threads\n");
            content.append("• Add `").append(toTag.getName()).append("` to those threads\n\n");
        }

        content.append("**Are you sure you want to proceed?**");

        ActionRow confirmRow = ActionRow.of(
            Button.success("tag-confirm-execute-" + userId + "-" + channelId + "-" + fromTagId + "-" + toTagId, "✅ Execute Transfer"),
            Button.danger("tag-confirm-cancel-" + userId + "-" + channelId + "-" + fromTagId + "-" + toTagId, "❌ Cancel")
        );

        ActionRow backRow = ActionRow.of(
            Button.secondary("tag-back-" + userId, "⬅️ Start Over")
        );

        hook.editOriginal(content.toString())
            .setComponents(confirmRow, backRow)
            .queue();
    }

    private void executeTagTransfer(InteractionHook hook, String userId, String channelId, String fromTagId, String toTagId) {
        ForumChannel channel = DiscordUtils.getMainGuild().getForumChannelById(channelId);
        ForumTag fromTag = channel != null ? channel.getAvailableTagById(fromTagId) : null;
        ForumTag toTag = channel != null ? channel.getAvailableTagById(toTagId) : null;

        if (channel == null || fromTag == null || toTag == null) {
            createTagTransferPanel(hook, userId, "❌ Invalid channel or tags!");
            return;
        }

        StringBuilder content = new StringBuilder();
        content.append("🏷️ **Tag Transfer in Progress**\n\n");
        content.append("📍 **Channel:** ").append(channel.getName()).append("\n");
        content.append("📤 **From:** `").append(fromTag.getName()).append("`\n");
        content.append("📥 **To:** `").append(toTag.getName()).append("`\n\n");
        content.append("🔄 **Status:** Loading threads...");

        hook.editOriginal(content.toString()).setComponents().queue();

        // Load affected threads
        List<ThreadChannel> threadChannels = Stream.concat(
                channel.getThreadChannels().stream(),
                channel.retrieveArchivedPublicThreadChannels().stream()
            )
            .filter(thread -> thread.getAppliedTags().contains(fromTag))
            .distinct()
            .toList();

        int total = threadChannels.size();
        if (total == 0) {
            createTagTransferPanel(hook, userId, "❌ No threads found with the source tag!");
            return;
        }

        // Update status with progress
        content = new StringBuilder();
        content.append("🏷️ **Tag Transfer in Progress**\n\n");
        content.append("📍 **Channel:** ").append(channel.getName()).append("\n");
        content.append("📤 **From:** `").append(fromTag.getName()).append("`\n");
        content.append("📥 **To:** `").append(toTag.getName()).append("`\n\n");
        content.append("📊 **Found:** ").append(total).append(" threads to update\n");
        content.append("🔄 **Status:** Processing threads...");

        hook.editOriginal(content.toString()).queue();

        // Process threads
        int processed = 0;
        int errors = 0;

        for (ThreadChannel threadChannel : threadChannels) {
            try {
                List<ForumTag> threadTags = new ArrayList<>(threadChannel.getAppliedTags());
                threadTags.remove(fromTag);

                if (!threadTags.contains(toTag)) {
                    threadTags.add(toTag);
                }

                boolean wasArchived = threadChannel.isArchived();
                if (wasArchived) {
                    threadChannel.getManager().setArchived(false).complete();
                }

                threadChannel.getManager().setAppliedTags(threadTags).complete();

                if (wasArchived) {
                    threadChannel.getManager().setArchived(true).complete();
                }

                processed++;
            } catch (Exception e) {
                errors++;
                log.warn("Failed to update thread {}: {}", threadChannel.getId(), e.getMessage());
            }
        }

        // Final result
        content = new StringBuilder();
        content.append("🏷️ **Tag Transfer Complete**\n\n");
        content.append("📍 **Channel:** ").append(channel.getName()).append("\n");
        content.append("📤 **From:** `").append(fromTag.getName()).append("`\n");
        content.append("📥 **To:** `").append(toTag.getName()).append("`\n\n");
        content.append("✅ **Successfully processed:** ").append(processed).append(" threads\n");
        if (errors > 0) {
            content.append("❌ **Errors:** ").append(errors).append(" threads\n");
        }
        content.append("\n**Transfer completed successfully!**");

        ActionRow newTransferRow = ActionRow.of(
            Button.primary("tag-back-" + userId, "🔄 Start New Transfer")
        );

        hook.editOriginal(content.toString())
            .setComponents(newTransferRow)
            .queue();
    }

    @SlashAutocompleteHandler(id = "forumchannels")
    public List<Command.Choice> listForumChannels(CommandAutoCompleteInteractionEvent event) {
        return DiscordUtils.getMainGuild().getForumChannels().stream()
            .map(channel -> new Command.Choice(channel.getName(), channel.getId()))
            .filter(choice -> choice.getName().toLowerCase().contains(event.getFocusedOption().getValue().toLowerCase()))
            .sorted(Comparator.comparing(Command.Choice::getName))
            .toList();
    }

    @SlashAutocompleteHandler(id = "forumtags")
    public List<Command.Choice> listForumTags(CommandAutoCompleteInteractionEvent event) {
        List<ForumTag> forumTags = new ArrayList<>();

        ChannelCache.getForumChannelById(event.getOption("channel").getAsString()).ifPresent(forumChannel -> {
            forumTags.addAll(forumChannel.getAvailableTags());
        });

        return forumTags.stream()
            .map(tag -> new Command.Choice(tag.getName(), tag.getId()))
            .toList();
    }

    @SlashCommand(name = "force", subcommand = "nominations", description = "Forcefully run the nomination process", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void forceNominations(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Only check users with the New Member role", required = false) Boolean newMembersOnly
    ) {
        boolean onlyNew = newMembersOnly != null && newMembersOnly;
        if (onlyNew) {
            NominationSweepReport report = NominationService.getInstance().runNewMemberNominationSweep();
            EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Nomination Sweep Complete (New Member)")
                .setColor(Color.GREEN)
                .addField("Scanned", String.valueOf(report.scanned()), true)
                .addField("Eligible", String.valueOf(report.eligible()), true)
                .addField("Nominated", String.valueOf(report.nominated()), true)
                .addField("Below Threshold", String.valueOf(report.belowThreshold()), true)
                .addField("Already This Month", String.valueOf(report.alreadyThisMonth()), true)
                .addField("Ineligible", String.valueOf(report.ineligible()), true)
                .addField("Missing Member", String.valueOf(report.missingMember()), true)
                .addField("Duration", report.durationMs() + "ms", true);
            event.replyEmbeds(eb.build()).queue();
        } else {
            NominationSweepReport report = NominationService.getInstance().runMemberNominationSweep();
            EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Nomination Sweep Complete (Member)")
                .setColor(Color.GREEN)
                .addField("Scanned", String.valueOf(report.scanned()), true)
                .addField("Eligible", String.valueOf(report.eligible()), true)
                .addField("Nominated", String.valueOf(report.nominated()), true)
                .addField("Below Threshold", String.valueOf(report.belowThreshold()), true)
                .addField("Already This Month", String.valueOf(report.alreadyThisMonth()), true)
                .addField("Ineligible", String.valueOf(report.ineligible()), true)
                .addField("Missing Member", String.valueOf(report.missingMember()), true)
                .addField("Duration", report.durationMs() + "ms", true);
            event.replyEmbeds(eb.build()).queue();
        }
    }

    @SlashCommand(name = "force", subcommand = "inactivity-check", description = "Forcefully run the inactivity check", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void forceInactiveCheck(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Only check users with the New Member role", required = false) Boolean newMembersOnly
    ) {
        boolean onlyNew = newMembersOnly != null && newMembersOnly;
        if (onlyNew) {
            InactivitySweepReport report = NominationInactivityService.getInstance().runInactivitySweepForNewMembers();
            EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Inactivity Sweep Complete (New Member)")
                .setColor(Color.ORANGE)
                .addField("Scanned", String.valueOf(report.scanned()), true)
                .addField("Warned This Month", String.valueOf(report.warnedThisMonth()), true)
                .addField("Skipped Already This Month", String.valueOf(report.skippedAlreadyThisMonth()), true)
                .addField("Ineligible", String.valueOf(report.ineligible()), true)
                .addField("Missing Member", String.valueOf(report.missingMember()), true)
                .addField("Duration", report.durationMs() + "ms", true);
            event.replyEmbeds(eb.build()).queue();
        } else {
            InactivitySweepReport report = NominationInactivityService.getInstance().runInactivitySweepForMembers();
            EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Inactivity Sweep Complete (Member)")
                .setColor(Color.ORANGE)
                .addField("Scanned", String.valueOf(report.scanned()), true)
                .addField("Warned This Month", String.valueOf(report.warnedThisMonth()), true)
                .addField("Skipped Already This Month", String.valueOf(report.skippedAlreadyThisMonth()), true)
                .addField("Ineligible", String.valueOf(report.ineligible()), true)
                .addField("Missing Member", String.valueOf(report.missingMember()), true)
                .addField("Duration", report.durationMs() + "ms", true);
            event.replyEmbeds(eb.build()).queue();
        }
    }

    @SlashCommand(name = "force", subcommand = "restricted-inactivity-check", description = "Forcefully run the restricted inactivity check", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void forceRestrictedInactiveCheck(SlashCommandInteractionEvent event) {
        NominationInactivityService.getInstance().runRoleRestrictedInactivitySweep();
        event.reply("Forced restricted inactivity check!").queue();
    }

    @SlashCommand(name = "reset-inactivity-check-data", description = "Reset the inactivity check data", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void resetInactiveCheckData(SlashCommandInteractionEvent event) {
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        discordUserRepository.forEach(discordUser -> {
            LastActivity lastActivity = discordUser.getLastActivity();
            NominationInfo nominationInfo = lastActivity.getNominationInfo();

            nominationInfo.setTotalInactivityWarnings(0);
            nominationInfo.setLastInactivityWarningTimestamp(null);
        });
    }

    @SlashCommand(name = "scan-channel", description = "Manually scan a channel for role membership", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void scanChannelForRoleRestricted(SlashCommandInteractionEvent event, @SlashOption(description = "The channel to scan") GuildChannel channel) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            event.getHook().editOriginal("User not found").queue();
            return;
        }

        NerdBotConfig botConfig = SkyBlockNerdsBot.config();

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

        NerdBotConfig updatedConfig = SkyBlockNerdsBot.config();
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

    @SlashCommand(name = "channel-config", subcommand = "toggle", description = "Toggle automatic management of role-restricted channel groups", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void toggleAutoManageRoleRestricted(SlashCommandInteractionEvent event) {
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            event.reply("User not found").queue();
            return;
        }

        NerdBotConfig botConfig = SkyBlockNerdsBot.config();
        boolean currentState = botConfig.getChannelConfig().isAutoManageRoleRestrictedChannels();
        botConfig.getChannelConfig().setAutoManageRoleRestrictedChannels(!currentState);

        DiscordBotEnvironment.getBot().writeConfig(botConfig);

        String status = !currentState ? "enabled" : "disabled";
        event.reply("✅ Automatic management of role-restricted channel groups has been **" + status + "**.").setEphemeral(true).queue();

        log.info("{} {} automatic management of role-restricted channel groups",
            event.getUser().getName(), !currentState ? "enabled" : "disabled");
    }

    @SlashCommand(name = "channel-config", subcommand = "status", description = "View the status of automatic role-restricted channel management", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void autoManageStatus(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            event.getHook().editOriginal("User not found").queue();
            return;
        }

        NerdBotConfig botConfig = SkyBlockNerdsBot.config();
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

    @SlashCommand(name = "channel-config", subcommand = "rebuild", description = "Rebuild role-restricted channel groups from current permissions", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void rebuildRoleRestrictedGroups(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            event.getHook().editOriginal("User not found").queue();
            return;
        }

        NerdBotConfig botConfig = SkyBlockNerdsBot.config();

        if (!botConfig.getChannelConfig().isAutoManageRoleRestrictedChannels()) {
            event.getHook().editOriginal("❌ Automatic channel management is disabled. Enable it first with `/channel-config toggle`").queue();
            return;
        }

        int groupsBefore = botConfig.getChannelConfig().getRoleRestrictedChannelGroups().size();
        int channelsBefore = botConfig.getChannelConfig().getRoleRestrictedChannelGroups().stream()
            .mapToInt(group -> group.getChannelIds().length)
            .sum();

        event.getHook().editOriginal("🔄 Scanning all guild channels and rebuilding groups... This may take a moment.").queue();

        BotEnvironment.EXECUTOR_SERVICE.execute(() -> {
            try {
                botConfig.getChannelConfig().rebuildRoleRestrictedChannelGroups();
                DiscordBotEnvironment.getBot().writeConfig(botConfig);

                NerdBotConfig finalConfig = SkyBlockNerdsBot.config();
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

    @SlashCommand(name = "channel-config", subcommand = "clean", description = "Remove empty role-restricted channel groups", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void cleanRoleRestrictedGroups(SlashCommandInteractionEvent event) {
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            event.reply("User not found").queue();
            return;
        }

        NerdBotConfig botConfig = SkyBlockNerdsBot.config();
        int groupsBefore = botConfig.getChannelConfig().getRoleRestrictedChannelGroups().size();

        boolean removed = botConfig.getChannelConfig().removeEmptyRoleRestrictedGroups();

        if (removed) {
            DiscordBotEnvironment.getBot().writeConfig(botConfig);
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

    @SlashCommand(name = "debug", subcommand = "ungrouped-channels", description = "Show channels that are not part of any role-restricted group", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void debugUngroupedChannels(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            event.getHook().editOriginal("User not found").queue();
            return;
        }

        NerdBotConfig botConfig = SkyBlockNerdsBot.config();
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
