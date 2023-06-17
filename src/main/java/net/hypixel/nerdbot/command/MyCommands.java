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
import net.hypixel.nerdbot.bot.config.EmojiConfig;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Log4j2
public class MyCommands extends ApplicationCommand {

    private static final List<String> GREENLIT_TAGS = Arrays.asList("greenlit", "docced");

    @JDASlashCommand(name = "suggestions", subcommand = "by-member", description = "View user suggestions.")
    public void mySuggestions(
        GuildSlashEvent event,
        @AppOption @Optional Integer page,
        @AppOption(description = "Member to view.") @Optional Member member,
        @AppOption(description = "Tags to filter for (comma separated).") @Optional String tags,
        @AppOption(description = "Words to filter title for.") @Optional String title,
        @AppOption(description = "Toggle alpha suggestions.") @Optional Boolean alpha
    ) {
        BotConfig config = NerdBotApp.getBot().getConfig();
        page = (page == null) ? 1 : page;
        final int pageNum = Math.max(page, 1);
        final Member searchMember = (member == null) ? event.getMember() : member;
        final boolean isAlpha = (alpha != null && alpha);
        final String[] suggestionForumIds = (alpha != null && alpha) ? config.getAlphaSuggestionForumIds() : config.getSuggestionForumIds();

        if (suggestionForumIds == null || suggestionForumIds.length == 0) {
            event.reply("No " + (isAlpha ? "alpha " : "") + "suggestion forums are setup in the config.").setEphemeral(true).queue();
            return;
        }

        List<Suggestion> suggestions = getSuggestions(suggestionForumIds, searchMember, tags, title, isAlpha);

        if (suggestions.isEmpty()) {
            event.reply("You have no suggestions matched with tags " + (tags != null ? ("`" + tags + "`") : "*ANY*") + "` or title containing " + (title != null ? ("`" + title + "`") : "*ANYTHING*") + ".").setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(buildSuggestionsEmbed(suggestions, tags, title, isAlpha, pageNum).setAuthor(searchMember.getEffectiveName()).build()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "suggestions", subcommand = "by-everyone", description = "View all suggestions.")
    public void allSuggestions(
        GuildSlashEvent event,
        @AppOption @Optional Integer page,
        @AppOption(description = "Tags to filter for (comma separated).") @Optional String tags,
        @AppOption(description = "Words to filter title for.") @Optional String title,
        @AppOption(description = "Toggle alpha suggestions.") @Optional Boolean alpha
    ) {
        BotConfig config = NerdBotApp.getBot().getConfig();
        page = (page == null) ? 1 : page;
        final int pageNum = Math.max(page, 1);
        final boolean isAlpha = (alpha != null && alpha);
        final String[] suggestionForumIds = isAlpha ? config.getAlphaSuggestionForumIds() : config.getSuggestionForumIds();

        if (suggestionForumIds == null || suggestionForumIds.length == 0) {
            event.reply("No " + (isAlpha ? "alpha " : "") + "suggestion forums are setup in the config.").setEphemeral(true).queue();
            return;
        }

        List<Suggestion> suggestions = getSuggestions(suggestionForumIds, null, tags, title, isAlpha);

        if (suggestions.isEmpty()) {
            event.reply("You have no suggestions matched with tags " + (tags != null ? ("`" + tags + "`") : "*ANY*") + "` or title containing " + (title != null ? ("`" + title + "`") : "*ANYTHING*") + ".").setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(buildSuggestionsEmbed(suggestions, tags, title, isAlpha, pageNum).build()).setEphemeral(true).queue();
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

    private static List<Suggestion> getSuggestions(String[] suggestionForumIds, Member member, String tags, String title, boolean alpha) {
        final List<String> searchTags = Arrays.asList(tags != null ? tags.split(", *") : new String[0]);

        return Arrays.stream(suggestionForumIds)
            .filter(Objects::nonNull)
            .map(forumId -> NerdBotApp.getBot().getJDA().getForumChannelById(forumId))
            .filter(Objects::nonNull)
            .flatMap(forumChannel -> forumChannel.getThreadChannels()
                .stream()
                .sorted((o1, o2) -> Long.compare(o2.getTimeCreated().toInstant().toEpochMilli(), o1.getTimeCreated().toInstant().toEpochMilli())) // Sort by most recent
                .filter(thread -> member == null || thread.getOwnerIdLong() == member.getIdLong())
                .filter(thread -> thread.getHistoryFromBeginning(1).complete().getRetrievedHistory().get(0) != null) // Lol
                .filter(thread -> thread.getAppliedTags()
                    .stream()
                    .anyMatch(forumTag -> searchTags.stream().anyMatch(forumTag.getName()::equalsIgnoreCase))
                )
                .filter(thread -> title == null || thread.getName()
                    .toLowerCase()
                    .contains(title.toLowerCase())
                )
                .map(thread -> new Suggestion(thread, alpha))
            )
            .toList();
    }

    private static EmbedBuilder buildSuggestionsEmbed(List<Suggestion> suggestions, String tags, String title, boolean alpha, int pageNum) {
        EmojiConfig emojiConfig = NerdBotApp.getBot().getConfig().getEmojiConfig();
        List<Suggestion> pages = InfoCommands.getPage(suggestions, pageNum, 10);
        int totalPages = (int) Math.ceil(suggestions.size() / 10.0);
        List<List<String>> fieldData = new ArrayList<>();
        double total = suggestions.size();
        double greenlit = suggestions.stream().filter(Suggestion::isGreenlit).count();

        for (Suggestion suggestion : pages) {
            String name = suggestion.getThread().getName();
            if (name.length() > 50) {
                name = name.substring(0, 50);
                name += "...";
            }
            String link = "[" + name + "](" + suggestion.getThread().getJumpUrl() + ")" +
                (suggestion.isGreenlit() ? " <:creative:" + emojiConfig.getGreenlitEmojiId() + ">" : "");

            fieldData.add(Arrays.asList(
                link,
                String.valueOf(suggestion.getAgrees()),
                String.valueOf(suggestion.getDisagrees())
            ));
        }

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Color.GREEN)
            .setTitle("All Suggestions")
            .setDescription(
                (tags != null ? "- Filtered by tags: `" + tags + "`\n" : "") + (title != null ? "- Filtered by title: `" + title + "`" : "")
            )
            .addField(
                "Total",
                String.valueOf((int) total),
                true
            )
            .addField(
                ("<:creative:" + emojiConfig.getGreenlitEmojiId() + ">"),
                (int) greenlit + " (" + (int) ((greenlit / total) * 100.0) + "%)",
                true
            )
            .addBlankField(true)
            .addField(
                "Links",
                fieldData.stream()
                    .map(list -> list.get(0))
                    .collect(Collectors.joining("\n")),
                true
            )
            .addField(
                ("<:agree:" + emojiConfig.getAgreeEmojiId() + ">"),
                fieldData.stream()
                    .map(list -> list.get(1))
                    .collect(Collectors.joining("\n")),
                true
            )
            .addField(
                ("<:disagree:" + emojiConfig.getDisagreeEmojiId() + ">"),
                fieldData.stream()
                    .map(list -> list.get(2))
                    .collect(Collectors.joining("\n")),
                true
            )
            .setFooter("Page: " + pageNum + "/" + totalPages + " | Alpha: " + (alpha ? "Yes" : "No"));

        return embedBuilder;
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
