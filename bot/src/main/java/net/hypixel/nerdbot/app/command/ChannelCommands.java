package net.hypixel.nerdbot.app.command;

import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.config.objects.CustomForumTag;
import net.hypixel.nerdbot.discord.config.objects.ForumAutoTag;
import net.hypixel.nerdbot.discord.config.suggestion.SuggestionConfig;
import net.hypixel.nerdbot.discord.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.discord.storage.database.repository.DiscordUserRepository;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.DiscordUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class ChannelCommands {

    @SlashCommand(name = "archive", subcommand = "channel", description = "Archives a channel and exports its contents to a file", guildOnly = true, defaultMemberPermissions = {"MANAGE_CHANNEL"}, requiredPermissions = {"MANAGE_CHANNEL"})
    public void archive(SlashCommandInteractionEvent event, @SlashOption TextChannel channel) {
        event.deferReply(true).complete();
        InteractionHook hook = event.getHook();
        hook.editOriginal(String.format("Archiving channel %s! If no file appears here, please contact a bot developer.", channel.getAsMention())).queue();

        CompletableFuture.runAsync(() -> {
            try {
                File file = ArchiveExporter.exportTextChannel(channel, createProgressUpdater(hook));
                log.info("Finished archiving channel " + channel.getName() + " (ID: " + channel.getId() + ")! File located at: " + file.getAbsolutePath());

                if (!hook.isExpired()) {
                    hook.editOriginal(String.format("Finished archiving channel %s! The file should appear below.", channel.getAsMention()))
                        .setFiles(FileUpload.fromData(file))
                        .queue();
                }
            } catch (IOException exception) {
                log.error("An error occurred when archiving the channel " + channel.getId() + "!", exception);
                if (!hook.isExpired()) {
                    hook.editOriginal(String.format("An error occurred while archiving channel %s: %s", channel.getAsMention(), exception.getMessage())).queue();
                }
            }
        });
    }

    @SlashCommand(name = "archive", subcommand = "category", description = "Archives all text/forum channels (including threads) in a category into a single zip", guildOnly = true, defaultMemberPermissions = {"MANAGE_CHANNEL"}, requiredPermissions = {"MANAGE_CHANNEL"})
    public void archiveCategory(SlashCommandInteractionEvent event, @SlashOption Category category) {
        event.deferReply(true).complete();
        InteractionHook hook = event.getHook();
        hook.editOriginal(String.format("Archiving category **%s** (ID: %s). You will receive a DM when complete.", category.getName(), category.getId())).queue();

        CompletableFuture.runAsync(() -> {
            try {
                File zipFile = ArchiveExporter.exportCategory(category, createProgressUpdater(hook));
                if (!hook.isExpired()) {
                    hook.editOriginal("Finished archiving! Sending the zip file via DM").queue();
                }
                sendZipToUserAsync(event, zipFile, hook, category.getName());
            } catch (IOException exception) {
                log.error("Failed to zip archives for category " + category.getId(), exception);
                if (!hook.isExpired()) {
                    hook.editOriginal("Failed to create the archive zip file: " + exception.getMessage()).queue();
                } else {
                    event.getUser().openPrivateChannel().queue(
                        dm -> dm.sendMessage("Failed to archive category " + category.getName() + ": " + exception.getMessage()).queue(),
                        error -> log.error("Failed to notify user {} of archive failure via DM", event.getUser().getId(), error)
                    );
                }
            }
        });
    }

    private void sendZipToUser(SlashCommandInteractionEvent event, File zipFile, InteractionHook hook) {
        event.getUser().openPrivateChannel().queue(privateChannel -> {
            privateChannel.sendFiles(FileUpload.fromData(zipFile)).queue(
                success -> hook.editOriginal("Finished! Sent you the archive zip via DM").queue(),
                error -> {
                    log.error("Failed to DM archive zip file to user " + event.getUser().getId(), error);
                    hook.editOriginal("Finished archiving, but failed to DM the zip file. Sending here instead...")
                        .setFiles(FileUpload.fromData(zipFile))
                        .queue(null, sendError -> hook.editOriginal("Failed to send the zip. File path: " + zipFile.getAbsolutePath()).queue());
                }
            );
        }, error -> {
            log.error("Failed to open DM for user " + event.getUser().getId(), error);
            hook.editOriginal("Finished archiving, but could not open DMs to send the zip file. File path: " + zipFile.getAbsolutePath()).queue();
        });
    }

    private void sendZipToUserAsync(SlashCommandInteractionEvent event, File zipFile, InteractionHook hook, String categoryName) {
        event.getUser().openPrivateChannel().queue(privateChannel -> {
            privateChannel.sendMessage("Here is your archive for category **" + categoryName + "**:")
                .addFiles(FileUpload.fromData(zipFile))
                .queue(
                    success -> {
                        log.info("Successfully sent archive zip for category {} to user {}", categoryName, event.getUser().getId());
                        if (!hook.isExpired()) {
                            hook.editOriginal("Finished! Sent you the archive zip via DM.").queue();
                        }
                    },
                    error -> {
                        log.error("Failed to DM archive zip file to user " + event.getUser().getId(), error);
                        if (!hook.isExpired()) {
                            hook.editOriginal("Finished archiving, but failed to DM the zip file. Sending here instead...")
                                .setFiles(FileUpload.fromData(zipFile))
                                .queue(
                                    null,
                                    sendError -> hook.editOriginal("Failed to send the zip. File path: " + zipFile.getAbsolutePath()).queue()
                                );
                        } else {
                            log.warn("Hook expired and DM failed. Archive file located at: {}", zipFile.getAbsolutePath());
                        }
                    }
                );
        }, error -> {
            log.error("Failed to open DM for user " + event.getUser().getId(), error);
            if (!hook.isExpired()) {
                hook.editOriginal("Finished archiving, but could not open DMs to send the zip file. File path: " + zipFile.getAbsolutePath()).queue();
            } else {
                log.warn("Hook expired and could not open DM. Archive file located at: {}", zipFile.getAbsolutePath());
            }
        });
    }

    private Consumer<String> createProgressUpdater(InteractionHook hook) {
        AtomicLong lastUpdate = new AtomicLong(0);
        long intervalMs = 10_000;

        return status -> {
            log.info(status);

            if (hook.isExpired()) {
                return;
            }

            long now = System.currentTimeMillis();
            long last = lastUpdate.get();
            if (now - last >= intervalMs && lastUpdate.compareAndSet(last, now)) {
                hook.editOriginal(status).queue(
                    null,
                    throwable -> log.debug("Failed to send progress update: {}", throwable.getMessage())
                );
            }
        };
    }

    @SlashCommand(name = "lock", description = "Locks the thread that the command is executed in", guildOnly = true, defaultMemberPermissions = {"MANAGE_THREADS"}, requiredPermissions = {"MANAGE_THREADS"})
    public void lockThread(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();
        InteractionHook hook = event.getHook();

        hook.editOriginal("Locking...").queue();

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        discordUserRepository.findByIdAsync(event.getMember().getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    hook.editOriginal("User not found").queue();
                    return;
                }

                if (!(event.getChannel() instanceof ThreadChannel threadChannel) || !(threadChannel.getParentChannel() instanceof ForumChannel)) {
                    hook.editOriginal("This command is only available in threads!").queue();
                    return;
                }

                ForumChannel forumChannel = threadChannel.getParentChannel().asForumChannel();
                SuggestionConfig suggestionConfig = DiscordBotEnvironment.getBot().getConfig().getSuggestionConfig();
                boolean isSuggestion = threadChannel.getParentChannel().getId().equalsIgnoreCase(suggestionConfig.getForumChannelId());

                if (isSuggestion) {
                    if (!DiscordUtils.hasTagByName(forumChannel, suggestionConfig.getReviewedTag())) {
                        hook.editOriginal(String.format("I could not find a tag with the name `%s`!", suggestionConfig.getReviewedTag())).queue();
                        return;
                    }

                    handleTagAndLock(event, discordUser, threadChannel, forumChannel, suggestionConfig.getReviewedTag());
                } else {
                    Optional<CustomForumTag> customForumTag = DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getCustomForumTags().stream()
                        .filter(tag -> tag.getOwnerId() != null && tag.getOwnerId().equals(discordUser.getDiscordId()))
                        .findFirst();

                    if (customForumTag.isPresent()) {
                        handleTagAndLock(event, discordUser, threadChannel, forumChannel, customForumTag.get().getName());
                    } else {
                        handleTagAndLock(event, discordUser, threadChannel, forumChannel, suggestionConfig.getReviewedTag());
                    }
                }
            })
            .exceptionally(throwable -> {
                log.error("Error loading user for channel lock", throwable);
                hook.editOriginal("Failed to load user data").queue();
                return null;
            });
    }

    private void handleTagAndLock(SlashCommandInteractionEvent event, DiscordUser discordUser, ThreadChannel threadChannel, ForumChannel forumChannel, String tagName) {
        InteractionHook hook = event.getHook();

        if (!DiscordUtils.hasTagByName(forumChannel, tagName)) {
            hook.editOriginal(String.format("I could not find a tag with the name `%s`!", tagName)).queue();
            return;
        }

        ThreadChannelManager threadManager = threadChannel.getManager();
        ForumTag tag = DiscordUtils.getTagByName(forumChannel, tagName);

        if (!threadChannel.getAppliedTags().contains(tag)) {
            List<ForumTag> appliedTags = new ArrayList<>(threadChannel.getAppliedTags());
            appliedTags.add(tag);

            // Check for auto-tag swap configuration
            ForumAutoTag autoTagConfig = DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getForumAutoTagConfig(forumChannel.getId());
            if (autoTagConfig != null && autoTagConfig.getReviewTagName().equalsIgnoreCase(tagName)) {
                ForumTag defaultTag = DiscordUtils.getTagByName(forumChannel, autoTagConfig.getDefaultTagName());
                if (defaultTag != null && appliedTags.contains(defaultTag)) {
                    appliedTags.remove(defaultTag);
                    log.info("Removed auto-tag '{}' from thread '{}' (ID: {}) when '{}' tag was applied via /lock", autoTagConfig.getDefaultTagName(), threadChannel.getName(), threadChannel.getId(), tagName);
                }
            }

            threadManager.setAppliedTags(appliedTags).complete();
            threadManager.setLocked(true).complete();
            event.getChannel().sendMessage(String.format("%s applied the %s tag and locked this suggestion!", event.getUser().getAsMention(), tag.getName())).queue();
            hook.editOriginal("Successfully locked thread!").queue();
        } else {
            hook.editOriginal("This thread is already tagged!").queue();
        }
    }
}