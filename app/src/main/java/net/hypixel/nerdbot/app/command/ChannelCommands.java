package net.hypixel.nerdbot.app.command;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.hypixel.nerdbot.app.export.ArchiveExporter;
import net.hypixel.nerdbot.app.command.util.CommandReplies;
import net.hypixel.nerdbot.app.command.util.ProgressUpdates;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.config.SuggestionConfig;
import net.hypixel.nerdbot.discord.config.channel.ChannelConfig;
import net.hypixel.nerdbot.discord.config.objects.CustomForumTag;
import net.hypixel.nerdbot.discord.config.objects.ForumAutoTag;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
public class ChannelCommands {

    static final Duration PROGRESS_INTERVAL = Duration.ofSeconds(10);

    // Of the threads an archive had to leave out
    private static final int SKIPPED_NAMES_SHOWN = 10;

    private final ArchiveExporter exporter;
    private final Provider<NerdBotConfig> config;
    private final Executor backgroundWork;
    private final Clock clock;

    @Inject
    public ChannelCommands(ArchiveExporter exporter, Provider<NerdBotConfig> config, Executor backgroundWork, Clock clock) {
        this.exporter = exporter;
        this.config = config;
        this.backgroundWork = backgroundWork;
        this.clock = clock;
    }

    @SlashCommand(name = "archive", subcommand = "channel", description = "Archives a channel and exports its contents to a file", guildOnly = true, defaultMemberPermissions = {"MANAGE_CHANNEL"}, requiredPermissions = {"MANAGE_CHANNEL"})
    public void archive(SlashCommandInteractionEvent event, @SlashOption TextChannel channel) {
        archiveChannel(event, channel);
    }

    @SlashCommand(name = "archive", subcommand = "category", description = "Archives all text/forum channels (including threads) in a category into a single zip", guildOnly = true, defaultMemberPermissions = {"MANAGE_CHANNEL"}, requiredPermissions = {"MANAGE_CHANNEL"})
    public void archiveCategory(SlashCommandInteractionEvent event, @SlashOption Category category) {
        archiveCategory((IReplyCallback) event, category);
    }

    void archiveChannel(IReplyCallback interaction, TextChannel channel) {
        InteractionHook hook = CommandReplies.deferPrivately(interaction);
        hook.editOriginal(String.format("Archiving channel %s! If no file appears here, please contact a bot developer.", channel.getAsMention())).queue();

        ProgressUpdates progress = ProgressUpdates.throttledReply(hook, PROGRESS_INTERVAL, clock);
        CompletableFuture.runAsync(() -> {
            try {
                ArchiveExporter.Export export = exporter.exportTextChannel(channel, progress.loggingReporter());
                progress.finish(() -> {
                    if (!hook.isExpired()) {
                        hook.editOriginal(String.format("Finished archiving channel %s! The file should appear below.", channel.getAsMention()) + skippedNote(export))
                            .setFiles(export.file().upload())
                            .queue();
                    }
                });
            } catch (RuntimeException exception) {
                CommandReplies.logFailure("An error occurred when archiving the channel " + channel.getId(), exception);
                progress.finish(() -> {
                    if (!hook.isExpired()) {
                        hook.editOriginal(String.format("An error occurred while archiving channel %s. Please try again later.", channel.getAsMention())).queue();
                    }
                });
            }
        }, backgroundWork);
    }

    void archiveCategory(IReplyCallback interaction, Category category) {
        InteractionHook hook = CommandReplies.deferPrivately(interaction);
        User user = interaction.getUser();
        hook.editOriginal(String.format("Archiving category **%s** (ID: %s). You will receive a DM when complete.", category.getName(), category.getId())).queue();

        ProgressUpdates progress = ProgressUpdates.throttledReply(hook, PROGRESS_INTERVAL, clock);
        CompletableFuture.runAsync(() -> {
            try {
                ArchiveExporter.Export export = exporter.exportCategory(category, progress.loggingReporter());
                progress.finish(() -> {
                    if (!hook.isExpired()) {
                        hook.editOriginal("Finished archiving! Sending the zip file via DM").queue();
                    }
                });
                sendZipToUser(user, export, hook, category.getName());
            } catch (IOException | RuntimeException exception) {
                CommandReplies.logFailure("Failed to archive category " + category.getId(), exception);
                progress.finish(() -> {
                });
                if (!hook.isExpired()) {
                    hook.editOriginal("Failed to create the archive zip file. Please try again later.").queue();
                } else {
                    user.openPrivateChannel().queue(
                        dm -> dm.sendMessage("Failed to archive category " + category.getName() + ". Please try again later.").queue(),
                        error -> log.error("Failed to notify user {} of archive failure via DM", user.getId(), error)
                    );
                }
            }
        }, backgroundWork);
    }

    /** Names the threads left out of an archive, so a partial archive isn't mistaken for a complete one. */
    static String skippedNote(ArchiveExporter.Export export) {
        List<String> skipped = export.skippedThreads();
        if (skipped.isEmpty()) {
            return "";
        }

        String names = String.join(", ", skipped.subList(0, Math.min(SKIPPED_NAMES_SHOWN, skipped.size())));
        String more = skipped.size() > SKIPPED_NAMES_SHOWN ? " and " + (skipped.size() - SKIPPED_NAMES_SHOWN) + " more" : "";
        return "\nLeft out because they couldn't be read: " + names + more;
    }

    @SlashCommand(name = "lock", description = "Locks the thread that the command is executed in", guildOnly = true, defaultMemberPermissions = {"MANAGE_THREADS"}, requiredPermissions = {"MANAGE_THREADS"})
    public void lockThread(SlashCommandInteractionEvent event) {
        lock(event, event.getChannel());
    }

    void lock(IReplyCallback interaction, @Nullable MessageChannelUnion channel) {
        InteractionHook hook = CommandReplies.deferPrivately(interaction);

        if (channel == null || !channel.getType().isThread()) {
            hook.editOriginal("This command is only available in threads!").queue();
            return;
        }

        ThreadChannel thread = channel.asThreadChannel();
        if (thread.getParentChannel().getType() != ChannelType.FORUM) {
            hook.editOriginal("This command is only available in threads!").queue();
            return;
        }

        ForumChannel forum = thread.getParentChannel().asForumChannel();
        String tagName = tagNameFor(forum.getId(), interaction.getUser().getId());
        Optional<ForumTag> tag = DiscordUtils.findTagByName(forum, tagName);
        if (tag.isEmpty()) {
            hook.editOriginal(String.format("I could not find a tag with the name `%s`!", tagName)).queue();
            return;
        }

        ForumTag autoDefault = autoDefaultTag(forum, tagName).orElse(null);
        Optional<List<ForumTag>> tagsToApply = tagsAfterLock(thread.getAppliedTags(), tag.get(), autoDefault, thread.isLocked());
        if (tagsToApply.isEmpty()) {
            hook.editOriginal("This thread is already tagged and locked!").queue();
            return;
        }

        boolean newlyTagged = !thread.getAppliedTags().contains(tag.get());
        thread.getManager().setAppliedTags(tagsToApply.get()).setLocked(true).queue(success -> {
            String announcement = newlyTagged
                ? String.format("%s applied the %s tag and locked this suggestion!", interaction.getUser().getAsMention(), tag.get().getName())
                : String.format("%s locked this suggestion!", interaction.getUser().getAsMention());
            channel.sendMessage(announcement).queue();
            hook.editOriginal("Successfully locked thread!").queue();
        }, failure -> CommandReplies.fail(hook, "Failed to lock the thread. Please check my permissions and try again.", failure));
    }

    /**
     * The tags the thread should end up with, or empty when it already has the tag and is locked.
     * When the forum swaps an automatic tag for this one, the automatic tag is removed.
     */
    static Optional<List<ForumTag>> tagsAfterLock(List<ForumTag> applied, ForumTag tag, @Nullable ForumTag autoDefault, boolean locked) {
        if (applied.contains(tag) && locked) {
            return Optional.empty();
        }

        List<ForumTag> tags = new ArrayList<>(applied);
        if (!tags.contains(tag)) {
            tags.add(tag);
        }
        if (autoDefault != null) {
            tags.remove(autoDefault);
        }
        return Optional.of(tags);
    }

    /** Suggestions get the reviewed tag; elsewhere a member with their own tag gets that one. */
    private String tagNameFor(String forumId, String userId) {
        NerdBotConfig current = config.get();
        SuggestionConfig suggestionConfig = current.getSuggestionConfig();
        if (forumId.equalsIgnoreCase(suggestionConfig.getForumChannelId())) {
            return suggestionConfig.getReviewedTag();
        }

        return current.getChannelConfig().getCustomForumTags().stream()
            .filter(customTag -> userId.equals(customTag.getOwnerId()))
            .map(CustomForumTag::getName)
            .findFirst()
            .orElse(suggestionConfig.getReviewedTag());
    }

    private Optional<ForumTag> autoDefaultTag(ForumChannel forum, String tagName) {
        ChannelConfig channelConfig = config.get().getChannelConfig();
        ForumAutoTag autoTag = channelConfig.getForumAutoTagConfig(forum.getId());
        if (autoTag == null || !autoTag.getReviewTagName().equalsIgnoreCase(tagName)) {
            return Optional.empty();
        }
        return DiscordUtils.findTagByName(forum, autoTag.getDefaultTagName());
    }

    private void sendZipToUser(User user, ArchiveExporter.Export export, InteractionHook hook, String categoryName) {
        String note = skippedNote(export);
        user.openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage("Here is your archive for category **" + categoryName + "**:" + note)
            .addFiles(export.file().upload())
            .queue(
                success -> {
                    log.info("Sent the archive of category {} to user {}", categoryName, user.getId());
                    if (!hook.isExpired()) {
                        hook.editOriginal("Finished! Sent you the archive zip via DM.").queue();
                    }
                },
                error -> {
                    log.error("Failed to DM the archive of category {} to user {}", categoryName, user.getId(), error);
                    sendZipHere(hook, export, note);
                }
            ), error -> {
            log.error("Failed to open a DM with user {}", user.getId(), error);
            sendZipHere(hook, export, note);
        });
    }

    private static void sendZipHere(InteractionHook hook, ArchiveExporter.Export export, String note) {
        if (hook.isExpired()) {
            log.warn("Couldn't DM the archive {} and the interaction has expired, so it wasn't delivered", export.file().name());
            return;
        }

        hook.editOriginal("Finished archiving, but couldn't DM you the zip file, so here it is instead." + note)
            .setFiles(export.file().upload())
            .queue(null, error -> hook.editOriginal("Finished archiving, but the zip file couldn't be sent. It may be too large for Discord.").queue());
    }
}
