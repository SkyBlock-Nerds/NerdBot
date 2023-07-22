package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.mongodb.Function;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.EmojiConfig;
import net.hypixel.nerdbot.util.discord.SuggestionCache;

import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Log4j2
public class SuggestionCommands extends ApplicationCommand {

    @JDASlashCommand(name = "suggestions", subcommand = "by-id", description = "View user suggestions.")
    public void viewMemberSuggestions(
        GuildSlashEvent event,
        @AppOption(description = "User ID to view.") Long userID,
        @AppOption @Optional Integer page,
        @AppOption(description = "Tags to filter for (comma separated).") @Optional String tags,
        @AppOption(description = "Words to filter title for.") @Optional String title,
        @AppOption(description = "Toggle alpha suggestions.") @Optional Boolean alpha
    ) {
        event.deferReply(true).queue();
        page = (page == null) ? 1 : page;
        final int pageNum = Math.max(page, 1);
        final User searchUser = NerdBotApp.getBot().getJDA().getUserById(userID);
        final boolean isAlpha = (alpha != null && alpha);

        List<SuggestionCache.Suggestion> suggestions = getSuggestions(userID, tags, title, isAlpha);

        if (suggestions.isEmpty()) {
            event.getHook().editOriginal("Found no suggestions matching the specified filters!").queue();
            return;
        }

        event.getHook().editOriginalEmbeds(
            buildSuggestionsEmbed(suggestions, tags, title, isAlpha, pageNum, false)
                .setAuthor(searchUser != null ? searchUser.getName() : String.valueOf(userID))
                .setThumbnail(searchUser != null ? searchUser.getEffectiveAvatarUrl() : null)
                .build()
        ).queue();
    }

    @JDASlashCommand(name = "suggestions", subcommand = "by-member", description = "View user suggestions.")
    public void viewMemberSuggestions(
        GuildSlashEvent event,
        @AppOption(description = "Member to view.") Member member,
        @AppOption @Optional Integer page,
        @AppOption(description = "Tags to filter for (comma separated).") @Optional String tags,
        @AppOption(description = "Words to filter title for.") @Optional String title,
        @AppOption(description = "Toggle alpha suggestions.") @Optional Boolean alpha
    ) {
        event.deferReply(true).queue();
        page = (page == null) ? 1 : page;
        final int pageNum = Math.max(page, 1);
        final boolean isAlpha = (alpha != null && alpha);

        List<SuggestionCache.Suggestion> suggestions = getSuggestions(member.getIdLong(), tags, title, isAlpha);

        if (suggestions.isEmpty()) {
            event.getHook().editOriginal("Found no suggestions matching the specified filters!").queue();
            return;
        }

        event.getHook().editOriginalEmbeds(
            buildSuggestionsEmbed(suggestions, tags, title, isAlpha, pageNum, false)
                .setAuthor(member.getEffectiveName())
                .setThumbnail(member.getEffectiveAvatarUrl())
                .build()
        ).queue();
    }

    @JDASlashCommand(name = "suggestions", subcommand = "by-everyone", description = "View all suggestions.")
    public void viewAllSuggestions(
        GuildSlashEvent event,
        @AppOption @Optional Integer page,
        @AppOption(description = "Tags to filter for (comma separated).") @Optional String tags,
        @AppOption(description = "Words to filter title for.") @Optional String title,
        @AppOption(description = "Toggle alpha suggestions.") @Optional Boolean alpha
    ) {
        event.deferReply(true).queue();
        page = (page == null) ? 1 : page;
        final int pageNum = Math.max(page, 1);
        final boolean isAlpha = (alpha != null && alpha);

        List<SuggestionCache.Suggestion> suggestions = getSuggestions(null, tags, title, isAlpha);

        if (suggestions.isEmpty()) {
            event.getHook().editOriginal("Found no suggestions matching the specified filters!").queue();
            return;
        }

        event.getHook().editOriginalEmbeds(buildSuggestionsEmbed(suggestions, tags, title, isAlpha, pageNum, true).build()).queue();
    }

    public static List<SuggestionCache.Suggestion> getSuggestions(Long userID, String tags, String title, boolean alpha) {
        final List<String> searchTags = Arrays.asList(tags != null ? tags.split(", *") : new String[0]);

        if (NerdBotApp.getSuggestionCache().getSuggestions().isEmpty()) {
            log.info("Suggestions cache is empty!");
            return Collections.emptyList();
        }

        return NerdBotApp.getSuggestionCache()
            .getSuggestions()
            .stream()
            .filter(suggestion -> suggestion.isAlpha() == alpha)
            .filter(SuggestionCache.Suggestion::notDeleted)
            .filter(suggestion -> userID == null || suggestion.getThread().getOwnerIdLong() == userID)
            .filter(suggestion -> searchTags.isEmpty() || searchTags.stream().allMatch(tag -> suggestion.getThread()
                .getAppliedTags()
                .stream()
                .anyMatch(forumTag -> forumTag.getName().equalsIgnoreCase(tag))
            ))
            .filter(suggestion -> title == null || suggestion.getThread()
                .getName()
                .toLowerCase()
                .contains(title.toLowerCase())
            )
            .toList();
    }

    public static EmbedBuilder buildSuggestionsEmbed(List<SuggestionCache.Suggestion> suggestions, String tags, String title, boolean alpha, int pageNum, boolean showNames) {
        List<SuggestionCache.Suggestion> pages = InfoCommands.getPage(suggestions, pageNum, 10);
        int totalPages = (int) Math.ceil(suggestions.size() / 10.0);

        StringJoiner links = new StringJoiner("\n");
        double total = suggestions.size();
        double greenlit = suggestions.stream().filter(SuggestionCache.Suggestion::isGreenlit).count();
        String filters = (tags != null ? "- Filtered by tags: `" + tags + "`\n" : "") + (title != null ? "- Filtered by title: `" + title + "`\n" : "") + (alpha ? "- Filtered by Alpha: `Yes`" : "");

        for (SuggestionCache.Suggestion suggestion : pages) {
            String link = "[" + suggestion.getThreadName().replaceAll("`", "") + "](" + suggestion.getThread().getJumpUrl() + ")";
            link += (suggestion.isGreenlit() ? " " + getEmojiFormat(EmojiConfig::getGreenlitEmojiId) : "") + "\n";
            link += suggestion.getThread().getAppliedTags().stream().map(ForumTag::getName).collect(Collectors.joining(", ")) + "\n";
            link = link.replaceAll("\n\n", "\n");

            if (showNames) {
                User user = NerdBotApp.getBot().getJDA().getUserById(suggestion.getThread().getOwnerIdLong());
                if (user != null) {
                    link += "Created by " + user.getEffectiveName() + "\n";
                }
            }

            link += getEmojiFormat(EmojiConfig::getAgreeEmojiId) + " " + suggestion.getAgrees() + "\u3000" + getEmojiFormat(EmojiConfig::getDisagreeEmojiId) + " " + suggestion.getDisagrees() + "\n";
            links.add(link);
        }

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Color.GREEN)
            .setTitle("Suggestions")
            .setDescription(links.toString())
            .addField(
                "Total",
                String.valueOf((int) total),
                true
            )
            .addField(
                getEmojiFormat(EmojiConfig::getGreenlitEmojiId),
                (int) greenlit + " (" + (int) ((greenlit / total) * 100.0) + "%)",
                true
            )
            .addBlankField(true)
            .setFooter("Page: " + pageNum + "/" + totalPages + (NerdBotApp.getSuggestionCache().isLoaded() ? "" : " | Caching is in progress!"));

        if (!filters.isEmpty()) {
            embedBuilder.addField("Filters", filters,  false);
        }

        return embedBuilder;
    }

    private static String getEmojiFormat(Function<EmojiConfig, String> emojiIdFunction) {
        return NerdBotApp.getBot().getJDA().getEmojiById(emojiIdFunction.apply(NerdBotApp.getBot().getConfig().getEmojiConfig())).getFormatted();
    }

}
