package net.hypixel.nerdbot.app.command;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashComponentHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.hypixel.nerdbot.app.channel.ForumTagTransfer;
import net.hypixel.nerdbot.app.channel.LogChannel;
import net.hypixel.nerdbot.app.command.util.CommandReplies;
import net.hypixel.nerdbot.app.command.util.PanelId;
import net.hypixel.nerdbot.app.command.util.ProgressUpdates;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** /admin transfer-tag: a step-by-step panel for swapping one forum tag for another on every thread. */
@Slf4j
public class TagTransferCommands {

    static final PanelId CHANNEL_SELECT = PanelId.of("tag-channel-select", 1, 0);
    static final PanelId FROM_SELECT = PanelId.of("tag-from-select", 2, 0);
    static final PanelId TO_SELECT = PanelId.of("tag-to-select", 3, 0);
    static final PanelId CONFIRM = PanelId.of("tag-confirm", 5, 1);
    static final PanelId BACK = PanelId.of("tag-back", 1, 0);

    static final String NOT_YOUR_PANEL = "You can only use your own tag transferring interface!";
    private static final int MAX_OPTIONS = StringSelectMenu.OPTIONS_MAX_AMOUNT;
    private static final Duration PROGRESS_INTERVAL = Duration.ofSeconds(2);

    private final ForumTagTransfer transfer;
    private final LogChannel logChannel;
    private final Executor backgroundWork;
    private final Clock clock;

    @Inject
    public TagTransferCommands(ForumTagTransfer transfer, LogChannel logChannel, Executor backgroundWork, Clock clock) {
        this.transfer = transfer;
        this.logChannel = logChannel;
        this.backgroundWork = backgroundWork;
        this.clock = clock;
    }

    @SlashCommand(name = "admin", subcommand = "transfer-tag", description = "Interactive forum tag transfer panel", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void transferForumTag(SlashCommandInteractionEvent event) {
        open(event);
    }

    void open(IReplyCallback event) {
        showChannels(CommandReplies.deferPrivately(event), event.getGuild(), event.getUser().getId(), null);
    }

    @SlashComponentHandler(id = "tag-channel-select", patterns = {"tag-channel-select-*"})
    public void handleChannelSelect(StringSelectInteractionEvent event) {
        channelChosen(event);
    }

    void channelChosen(StringSelectInteraction event) {
        CHANNEL_SELECT.parseFor(event, event.getComponentId(), NOT_YOUR_PANEL).ifPresent(parts -> {
            event.deferEdit().queue();
            showFromTags(event.getHook(), event.getGuild(), parts.getFirst(), event.getValues().getFirst());
        });
    }

    @SlashComponentHandler(id = "tag-from-select", patterns = {"tag-from-select-*"})
    public void handleFromTagSelect(StringSelectInteractionEvent event) {
        fromTagChosen(event);
    }

    void fromTagChosen(StringSelectInteraction event) {
        FROM_SELECT.parseFor(event, event.getComponentId(), NOT_YOUR_PANEL).ifPresent(parts -> {
            event.deferEdit().queue();
            showToTags(event.getHook(), event.getGuild(), parts.get(0), parts.get(1), event.getValues().getFirst());
        });
    }

    @SlashComponentHandler(id = "tag-to-select", patterns = {"tag-to-select-*"})
    public void handleToTagSelect(StringSelectInteractionEvent event) {
        toTagChosen(event);
    }

    CompletableFuture<Void> toTagChosen(StringSelectInteraction event) {
        Optional<List<String>> parts = TO_SELECT.parseFor(event, event.getComponentId(), NOT_YOUR_PANEL);
        if (parts.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        event.deferEdit().queue();
        List<String> ids = parts.get();
        return CompletableFuture.runAsync(() -> showConfirmation(event.getHook(), event.getGuild(), ids.get(0), ids.get(1), ids.get(2), event.getValues().getFirst()), backgroundWork);
    }

    @SlashComponentHandler(id = "tag-confirm", patterns = {"tag-confirm-*"})
    public void handleTagConfirm(ButtonInteractionEvent event) {
        confirm(event);
    }

    CompletableFuture<Void> confirm(ComponentInteraction event) {
        Optional<List<String>> parts = CONFIRM.parseFor(event, event.getComponentId(), NOT_YOUR_PANEL);
        if (parts.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        event.deferEdit().queue();
        List<String> ids = parts.get();
        if (!ids.getFirst().equals("execute")) {
            showChannels(event.getHook(), event.getGuild(), ids.get(1), "❌ Tag transfer cancelled.");
            return CompletableFuture.completedFuture(null);
        }

        return execute(event.getHook(), event.getGuild(), event.getUser().getAsMention(), ids.get(1), ids.get(2), ids.get(3), ids.get(4));
    }

    @SlashComponentHandler(id = "tag-back", patterns = {"tag-back-*"})
    public void handleTagBack(ButtonInteractionEvent event) {
        back(event);
    }

    void back(ComponentInteraction event) {
        BACK.parseFor(event, event.getComponentId(), NOT_YOUR_PANEL).ifPresent(parts -> {
            event.deferEdit().queue();
            showChannels(event.getHook(), event.getGuild(), parts.getFirst(), null);
        });
    }

    private void showChannels(InteractionHook hook, Guild guild, String userId, @Nullable String notice) {
        List<ForumChannel> forums = guild.getForumChannels();
        if (forums.isEmpty()) {
            hook.editOriginal("❌ No forum channels found in this server!").setComponents().queue();
            return;
        }

        StringSelectMenu.Builder select = StringSelectMenu.create(CHANNEL_SELECT.build(userId))
            .setPlaceholder("🏛️ Select Forum Channel")
            .setRequiredRange(1, 1);
        forums.stream().limit(MAX_OPTIONS).forEach(forum -> select.addOption(forum.getName(), forum.getId(), "Transfer tags in " + forum.getName()));

        hook.editOriginal(header() + (notice == null ? "" : notice + "\n\n")
                + "**Step 1:** Select the forum channel where you want to transfer tags.\n\n"
                + "📊 **Available Forum Channels:** " + forums.size()
                + (forums.size() > MAX_OPTIONS ? " (showing the first " + MAX_OPTIONS + ")" : ""))
            .setComponents(ActionRow.of(select.build()))
            .queue();
    }

    private void showFromTags(InteractionHook hook, Guild guild, String userId, String channelId) {
        ForumChannel forum = guild.getForumChannelById(channelId);
        if (forum == null) {
            showChannels(hook, guild, userId, "❌ Forum channel not found!");
            return;
        }

        List<ForumTag> tags = forum.getAvailableTags();
        if (tags.isEmpty()) {
            showChannels(hook, guild, userId, "❌ No tags found in " + forum.getName() + "!");
            return;
        }

        hook.editOriginal(header() + "**Step 2:** Select the source tag to transfer FROM.\n\n"
                + "📍 **Selected Channel:** " + forum.getName() + "\n"
                + "🏷️ **Available Tags:** " + tags.size() + "\n\n")
            .setComponents(ActionRow.of(tagSelect(FROM_SELECT.build(userId, channelId), "🏷️ Select Source Tag (FROM)", tags, "Transfer FROM this tag")),
                ActionRow.of(Button.secondary(BACK.build(userId), "⬅️ Back to Channel Selection")))
            .queue();
    }

    private void showToTags(InteractionHook hook, Guild guild, String userId, String channelId, String fromTagId) {
        ForumChannel forum = guild.getForumChannelById(channelId);
        if (forum == null) {
            showChannels(hook, guild, userId, "❌ Forum channel not found!");
            return;
        }

        ForumTag from = forum.getAvailableTagById(fromTagId);
        List<ForumTag> tags = forum.getAvailableTags().stream().filter(tag -> !tag.getId().equals(fromTagId)).toList();
        if (from == null || tags.isEmpty()) {
            showFromTags(hook, guild, userId, channelId);
            return;
        }

        hook.editOriginal(header() + "**Step 3:** Select the destination tag to transfer TO.\n\n"
                + "📍 **Channel:** " + forum.getName() + "\n"
                + "📤 **From Tag:** " + from.getName() + "\n"
                + "🏷️ **Available Destination Tags:** " + tags.size() + "\n\n")
            .setComponents(ActionRow.of(tagSelect(TO_SELECT.build(userId, channelId, fromTagId), "🎯 Select Destination Tag (TO)", tags, "Transfer TO this tag")),
                ActionRow.of(Button.secondary(BACK.build(userId), "⬅️ Back to Channel Selection")))
            .queue();
    }

    /** Counting the threads pages through archived ones, so this runs off the dispatch thread. */
    private void showConfirmation(InteractionHook hook, Guild guild, String userId, String channelId, String fromTagId, String toTagId) {
        Selection selection = Selection.of(guild, channelId, fromTagId, toTagId);
        if (selection == null) {
            showChannels(hook, guild, userId, "❌ Invalid channel or tags!");
            return;
        }

        int affected = transfer.threadsWithTag(selection.forum(), selection.from()).size();
        StringBuilder content = new StringBuilder("🏷️ **Tag Transfer Confirmation**\n\n⚠️ **Please confirm the following transfer:**\n\n")
            .append(selection.describe())
            .append("📊 **Affected Threads:** ").append(affected).append("\n\n");

        if (affected == 0) {
            content.append("⚠️ **Warning:** No threads found with the source tag!\n\n");
        } else {
            content.append("🔄 **This will:**\n")
                .append("• Remove `").append(selection.from().getName()).append("` from ").append(affected).append(" threads\n")
                .append("• Add `").append(selection.to().getName()).append("` to those threads\n\n");
        }
        content.append("**Are you sure you want to proceed?**");

        hook.editOriginal(content.toString())
            .setComponents(
                ActionRow.of(
                    Button.success(CONFIRM.build("execute", userId, channelId, fromTagId, toTagId), "✅ Execute Transfer"),
                    Button.danger(CONFIRM.build("cancel", userId, channelId, fromTagId, toTagId), "❌ Cancel")),
                ActionRow.of(Button.secondary(BACK.build(userId), "⬅️ Start Over")))
            .queue();
    }

    private CompletableFuture<Void> execute(InteractionHook hook, Guild guild, String adminMention, String userId, String channelId, String fromTagId, String toTagId) {
        Selection selection = Selection.of(guild, channelId, fromTagId, toTagId);
        if (selection == null) {
            showChannels(hook, guild, userId, "❌ Invalid channel or tags!");
            return CompletableFuture.completedFuture(null);
        }

        hook.editOriginal("🏷️ **Tag Transfer in Progress**\n\n" + selection.describe() + "\n🔄 **Status:** Loading threads...").setComponents().queue();

        return CompletableFuture.supplyAsync(() -> transfer.threadsWithTag(selection.forum(), selection.from()), backgroundWork)
            .thenCompose(threads -> {
                if (threads.isEmpty()) {
                    showChannels(hook, guild, userId, "❌ No threads found with the source tag!");
                    return CompletableFuture.completedFuture(null);
                }

                ProgressUpdates progress = ProgressUpdates.throttledReply(hook, PROGRESS_INTERVAL, clock);
                return transfer.transfer(threads, selection.from(), selection.to(),
                        done -> progress.report(progressMessage(selection, done, threads.size())))
                    .thenAccept(outcome -> progress.finish(() -> {
                        hook.editOriginal(resultMessage(selection, outcome))
                            .setComponents(ActionRow.of(Button.primary(BACK.build(userId), "🔄 Start New Transfer")))
                            .queue();
                        // The panel can't be edited after 15 minutes, so a long transfer's result also goes to the log
                        logChannel.send(resultEmbed(adminMention, selection, outcome));
                    }));
            })
            .exceptionally(error -> {
                CommandReplies.fail(hook, "The tag transfer failed. Check the logs for details.", error);
                return null;
            });
    }

    private static StringSelectMenu tagSelect(String id, String placeholder, List<ForumTag> tags, String description) {
        StringSelectMenu.Builder select = StringSelectMenu.create(id).setPlaceholder(placeholder).setRequiredRange(1, 1);
        tags.stream().limit(MAX_OPTIONS).forEach(tag -> select.addOption(tag.getName(), tag.getId(), description));
        return select.build();
    }

    private static String header() {
        return "🏷️ **Tag Transfer Panel**\n\n";
    }

    static String progressMessage(Selection selection, int done, int total) {
        return "🏷️ **Tag Transfer in Progress**\n\n" + selection.describe() + "\n🔄 **Status:** " + done + "/" + total + " threads processed";
    }

    static String resultMessage(Selection selection, ForumTagTransfer.Outcome outcome) {
        StringBuilder content = new StringBuilder(outcome.failed() == 0 ? "🏷️ **Tag Transfer Complete**\n\n" : "🏷️ **Tag Transfer Finished With Errors**\n\n")
            .append(selection.describe())
            .append("\n✅ **Moved:** ").append(outcome.moved()).append(" threads\n");
        if (outcome.failed() > 0) {
            content.append("❌ **Failed:** ").append(outcome.failed()).append(" threads (see the logs)\n");
        }
        return content.toString();
    }

    private static MessageEmbed resultEmbed(String adminMention, Selection selection, ForumTagTransfer.Outcome outcome) {
        return new EmbedBuilder()
            .setTitle("Forum Tag Transfer")
            .setColor(outcome.failed() == 0 ? Color.GREEN : Color.ORANGE)
            .setDescription(adminMention + " moved `" + selection.from().getName() + "` to `" + selection.to().getName() + "` in " + selection.forum().getAsMention() + ".")
            .addField("Moved", String.valueOf(outcome.moved()), true)
            .addField("Failed", String.valueOf(outcome.failed()), true)
            .build();
    }

    /** The forum and both tags of a transfer, all still present. */
    record Selection(ForumChannel forum, ForumTag from, ForumTag to) {

        static @Nullable Selection of(Guild guild, String channelId, String fromTagId, String toTagId) {
            ForumChannel forum = guild.getForumChannelById(channelId);
            ForumTag from = forum == null ? null : forum.getAvailableTagById(fromTagId);
            ForumTag to = forum == null ? null : forum.getAvailableTagById(toTagId);
            return from == null || to == null ? null : new Selection(forum, from, to);
        }

        String describe() {
            return "📍 **Channel:** " + forum.getName() + "\n📤 **From:** `" + from.getName() + "`\n📥 **To:** `" + to.getName() + "`\n";
        }
    }
}
