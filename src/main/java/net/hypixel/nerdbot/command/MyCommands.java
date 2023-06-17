package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.bot.config.BotConfig;

import java.awt.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

@Log4j2
public class MyCommands extends ApplicationCommand {

    private static final List<String> GREENLIT_TAGS = Arrays.asList("greenlit", "docced");

    @JDASlashCommand(name = "suggestions", description = "View user suggestions.")
    public void mySuggestions(
        GuildSlashEvent event,
        @AppOption @Optional Integer page,
        @AppOption(description = "Member to view.") @Optional Member member,
        @AppOption(description = "Exact tag to filter for.") @Optional String tag,
        @AppOption(description = "Words to filter title for.") @Optional String title,
        @AppOption(description = "Toggle alpha suggestions.") @Optional Boolean alpha
    ) {
        BotConfig config = NerdBotApp.getBot().getConfig();
        page = (page == null) ? 1 : page;
        final int pageNum = Math.max(page, 1);
        final Member searchMember = (member == null) ? event.getMember() : member;
        final String[] suggestionForumIds = (alpha != null && alpha) ? config.getAlphaSuggestionForumIds() : config.getSuggestionForumIds();

        List<Suggestion> suggestions = Arrays.stream(suggestionForumIds)
            .filter(Objects::nonNull)
            .map(forumId -> NerdBotApp.getBot().getJDA().getForumChannelById(forumId))
            .filter(Objects::nonNull)
            .flatMap(forumChannel -> forumChannel.getThreadChannels()
                .stream()
                .sorted(Comparator.comparingLong(thread -> thread.getTimeCreated().toInstant().toEpochMilli())) // Sort by most recent
                .filter(thread -> thread.getOwnerIdLong() == searchMember.getIdLong())
                .filter(thread -> thread.getHistoryFromBeginning(1).complete().getRetrievedHistory().get(0) != null) // Lol
                .filter(thread -> tag == null || thread.getAppliedTags()
                    .stream()
                    .anyMatch(forumTag -> forumTag.getName().equalsIgnoreCase(tag))
                )
                .filter(thread -> title == null || thread.getName()
                    .toLowerCase()
                    .contains(title.toLowerCase())
                )
                .map(thread -> new Suggestion(thread, alpha))
            )
            .toList();

        if (suggestions.isEmpty()) {
            event.reply("You have no suggestions" + (tag != null ? " with the " + tag + " tag" : "") + ".").setEphemeral(true).queue();
            return;
        }

        List<Suggestion> pages = InfoCommands.getPage(suggestions, pageNum, 10);
        int totalPages = (int) Math.ceil(suggestions.size() / 10.0);
        StringJoiner description = new StringJoiner("\n");

        for (Suggestion suggestion : pages) {
            description.add("- [")
                .add(suggestion.getThread().getName())
                .add("](")
                .add(suggestion.getThread().getJumpUrl())
                .add(") ")
                .add("[<:agree:751221045926559875> **" + suggestion.getAgrees() + "** | ")
                .add("<:disagree:751221022954618942> **" + suggestion.getDisagrees() + "**]")
                .add(suggestion.isGreenlit() ? " <:creative:812826605243465758>" : "");
        }

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Color.GREEN)
            .setAuthor(searchMember.getEffectiveName())
            .setTitle((tag != null ? tag : "All") + " Suggestions")
            .setDescription(description.toString())
            .addField(
                "Total",
                String.valueOf(suggestions.size()),
                true
            )
            .addField(
                "Greenlit",
                String.valueOf(suggestions.stream().filter(Suggestion::isGreenlit).count()),
                true
            )
            .addBlankField(true)
            .setFooter("Page: " + pageNum + "/" + totalPages + " | Alpha: " + (alpha ? "Yes" : "No"));

        event.replyEmbeds(embedBuilder.build()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "activity", description = "View your recent activity.")
    public void myActivity(GuildSlashEvent event) {
        Pair<EmbedBuilder, EmbedBuilder> activityEmbeds = getActivityEmbeds(event.getMember());

        if (activityEmbeds.getLeft() == null || activityEmbeds.getRight() == null) {
            event.reply("Couldn't find that user in the database!").setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(activityEmbeds.getLeft().build(), activityEmbeds.getRight().build())
            .setEphemeral(true)
            .queue();
    }

    public static Pair<EmbedBuilder, EmbedBuilder> getActivityEmbeds(Member member) {
        Database database = NerdBotApp.getBot().getDatabase();
        DiscordUser discordUser;

        if (NerdBotApp.USER_CACHE.getIfPresent(member.getId()) != null) {
            discordUser = NerdBotApp.USER_CACHE.getIfPresent(member.getId());
        } else {
            discordUser = database.findDocument(database.getCollection("users", DiscordUser.class), "discordId", member.getId()).first();
        }

        // User not found in the database
        if (discordUser == null) {
            return Pair.of(null, null);
        }

        LastActivity lastActivity = discordUser.getLastActivity();
        EmbedBuilder globalEmbedBuilder = new EmbedBuilder();
        EmbedBuilder alphaEmbedBuilder = new EmbedBuilder();

        // Global Activity
        globalEmbedBuilder.setColor(Color.GREEN)
            .setAuthor(member.getEffectiveName() + " (" + member.getId() + ")")
            .setThumbnail(member.getEffectiveAvatarUrl())
            .setTitle("Last Global Activity")
            .addField("Most Recent", lastActivity.toRelativeTimestamp(LastActivity::getLastGlobalActivity), true)
            .addField("Voice Chat", lastActivity.toRelativeTimestamp(LastActivity::getLastVoiceChannelJoinDate), true)
            .addField("Item Generator", lastActivity.toRelativeTimestamp(LastActivity::getLastItemGenUsage), true)
            // Suggestions
            .addField("Created Suggestion", lastActivity.toRelativeTimestamp(LastActivity::getLastSuggestionDate), true)
            .addField("Voted on Suggestion", lastActivity.toRelativeTimestamp(LastActivity::getSuggestionVoteDate), true)
            .addField("New Comment", lastActivity.toRelativeTimestamp(LastActivity::getSuggestionCommentDate), true);

        // Alpha Activity
        alphaEmbedBuilder.setColor(Color.RED)
            .setTitle("Last Alpha Activity")
            .addField("Most Recent", lastActivity.toRelativeTimestamp(LastActivity::getLastAlphaActivity), true)
            .addField("Voice Chat", lastActivity.toRelativeTimestamp(LastActivity::getAlphaVoiceJoinDate), true)
            .addBlankField(true)
            // Suggestions
            .addField("Created Suggestion", lastActivity.toRelativeTimestamp(LastActivity::getLastAlphaSuggestionDate), true)
            .addField("Voted on Suggestion", lastActivity.toRelativeTimestamp(LastActivity::getAlphaSuggestionVoteDate), true)
            .addField("New Comment", lastActivity.toRelativeTimestamp(LastActivity::getAlphaSuggestionCommentDate), true);

        return Pair.of(globalEmbedBuilder, alphaEmbedBuilder);
    }

    @RequiredArgsConstructor
    private static class Suggestion {

        @Getter private final ThreadChannel thread;
        @Getter private final boolean alpha;
        @Getter private final int agrees;
        @Getter private final int disagrees;
        @Getter private final boolean greenlit;

        public Suggestion(ThreadChannel thread, boolean alpha) {
            this.thread = thread;
            this.alpha = alpha;
            MessageHistory history = thread.getHistoryFromBeginning(1).complete();
            Message message = history.getRetrievedHistory().get(0);
            this.agrees = getReactionCount(message, NerdBotApp.getBot().getConfig().getEmojiConfig().getAgreeEmojiId());
            this.disagrees = getReactionCount(message, NerdBotApp.getBot().getConfig().getEmojiConfig().getDisagreeEmojiId());
            this.greenlit = thread.getAppliedTags().stream().anyMatch(forumTag -> GREENLIT_TAGS.contains(forumTag.getName().toLowerCase()));
        }

        public static int getReactionCount(Message message, String emojiId) {
            return message.getReactions()
                .stream()
                .filter(reaction -> reaction.getEmoji().getType() == Emoji.Type.CUSTOM)
                .filter(reaction -> reaction.getEmoji()
                    .asCustom()
                    .getId()
                    .equalsIgnoreCase(emojiId)
                )
                .mapToInt(MessageReaction::getCount)
                .findFirst()
                .orElse(0);
        }

    }

}
