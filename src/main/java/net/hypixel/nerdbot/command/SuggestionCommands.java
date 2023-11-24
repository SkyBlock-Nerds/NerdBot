package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.SuggestionConfig;
import net.hypixel.nerdbot.cache.SuggestionCache;
import net.hypixel.nerdbot.channel.ChannelManager;
import org.apache.commons.lang.StringUtils;

import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

@Log4j2
public class SuggestionCommands extends ApplicationCommand {

    @JDASlashCommand(name = "request-review", description = "Request a greenlit review of your suggestion.")
    public void requestSuggestionReview(
        GuildSlashEvent event
    ) {
        if (event.getChannel().getType() != ChannelType.GUILD_PUBLIC_THREAD) {
            event.reply("This command is only usable in forum channels!").complete();
            return;
        }

        event.deferReply(true).complete();
        SuggestionCache.Suggestion suggestion = NerdBotApp.getSuggestionCache().getSuggestion(event.getChannel().getId());
        SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();

        // Handle User Deleted Posts
        if (suggestion.getFirstMessage().isEmpty()) {
            event.getHook().editOriginal("You appear to be lost, there is no original post to review!").complete();
            return;
        }

        // No Friends Allowed
        if (suggestion.getFirstMessage().get().getAuthor().getIdLong() != event.getUser().getIdLong()) {
            event.getHook().editOriginal("You can only request a review for your own posts!").complete();
            return;
        }

        // Handle Already Greenlit
        if (suggestion.isGreenlit()) {
            event.getHook().editOriginal("You appear to be confused, this post is already greenlit!").complete();
            return;
        }

        // Handle Minimum Agrees
        if (suggestion.getAgrees() < suggestionConfig.getRequestReviewThreshold()) {
            event.getHook().editOriginal(String.format("You need at least %s agrees to request a greenlit review!", suggestionConfig.getRequestReviewThreshold())).complete();
            return;
        }

        // Handle Greenlit Ratio
        if (suggestionConfig.isEnforcingGreenlitRatioForRequestReview() && suggestion.getRatio() <= suggestionConfig.getGreenlitRatio()) {
            event.getHook().editOriginal(String.format("You need at least %s%% agrees to request a greenlit review!", suggestionConfig.getGreenlitRatio())).complete();
            return;
        }

        // Send Request Review Message
        ChannelManager.getRequestedReviewChannel().ifPresentOrElse(textChannel -> {
            textChannel.sendMessageEmbeds(
                    new EmbedBuilder()
                        .setAuthor(String.format("Greenlit Review Request from %s", event.getUser().getEffectiveName()))
                        .setTitle(
                            suggestion.getThreadName(),
                            suggestion.getFirstMessage()
                                .map(Message::getJumpUrl)
                                .orElse(suggestion.getThread().getJumpUrl())
                        )
                        .setDescription(
                            suggestion.getFirstMessage()
                                .map(Message::getContentRaw)
                                .orElse("*Main message missing!*")
                        )
                        .setColor(Color.GRAY)
                        .setFooter(String.format(
                            "ID: %s",
                            suggestion.getThread().getId()
                        ))
                        .addField(
                            "Agrees",
                            String.valueOf(suggestion.getAgrees()),
                            true
                        )
                        .addField(
                            "Disagrees",
                            String.valueOf(suggestion.getDisagrees()),
                            true
                        )
                        .addField(
                            "Neutrals",
                            String.valueOf(suggestion.getNeutrals()),
                            true
                        )
                        .addField(
                            "Tags",
                            suggestion.getThread()
                                .getAppliedTags()
                                .stream()
                                .map(ForumTag::getName)
                                .collect(Collectors.joining(", ")),
                            false
                        )
                        .addField(
                            "Created",
                            suggestion.getFirstMessage()
                                .map(Message::getTimeCreated)
                                .map(date -> String.format("<t:%s:R>", date.toInstant().toEpochMilli() / 1000))
                                .orElse("?"),
                            false
                        )
                        .build()
                )
                .addActionRow(
                    net.dv8tion.jda.api.interactions.components.buttons.Button.of(
                        ButtonStyle.SUCCESS,
                        String.format(
                            "suggestion-review-accept-%s",
                            suggestion.getThread().getId()
                        ),
                        "Greenlit",
                        java.util.Optional.ofNullable(suggestionConfig.getGreenlitEmojiId())
                            .map(StringUtils::stripToNull)
                            .map(emojiId -> Emoji.fromCustom(
                                "greenlit",
                                Long.parseLong(emojiId),
                                false
                            ))
                            .orElse(null)
                    ),
                    Button.of(
                        ButtonStyle.DANGER,
                        String.format(
                            "suggestion-review-deny-%s",
                            suggestion.getThread().getId()
                        ),
                        "Deny",
                        java.util.Optional.ofNullable(suggestionConfig.getDisagreeEmojiId())
                            .map(StringUtils::stripToNull)
                            .map(emojiId -> Emoji.fromCustom(
                                "disagree",
                                Long.parseLong(emojiId),
                                false
                            ))
                            .orElse(null)
                    )
                )
                .queue();
        }, () -> {
            throw new RuntimeException("Requested review channel not found!");
        });

        // Respond to User
        event.getHook()
            .editOriginal("This suggestion has been sent for review.")
            .queue();
    }

    @JDASlashCommand(name = "suggestions", subcommand = "by-id", description = "View user suggestions.")
    public void viewMemberSuggestions(
        GuildSlashEvent event,
        @AppOption(description = "User ID to view.") Long userID,
        @AppOption @Optional Integer page,
        @AppOption(description = "Tags to filter for (comma separated).") @Optional String tags,
        @AppOption(description = "Words to filter title for.") @Optional String title,
        @AppOption(description = "Toggle alpha suggestions.") @Optional Boolean alpha
    ) {
        event.deferReply(true).complete();
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
            buildSuggestionsEmbed(suggestions, tags, title, isAlpha, pageNum, false, userID == event.getMember().getIdLong())
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
        event.deferReply(true).complete();
        page = (page == null) ? 1 : page;
        final int pageNum = Math.max(page, 1);
        final boolean isAlpha = (alpha != null && alpha);

        List<SuggestionCache.Suggestion> suggestions = getSuggestions(member.getIdLong(), tags, title, isAlpha);

        if (suggestions.isEmpty()) {
            event.getHook().editOriginal("Found no suggestions matching the specified filters!").queue();
            return;
        }

        event.getHook().editOriginalEmbeds(
            buildSuggestionsEmbed(suggestions, tags, title, isAlpha, pageNum, false, member.getIdLong() == event.getMember().getIdLong())
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
        event.deferReply(true).complete();
        page = (page == null) ? 1 : page;
        final int pageNum = Math.max(page, 1);
        final boolean isAlpha = (alpha != null && alpha);

        List<SuggestionCache.Suggestion> suggestions = getSuggestions(null, tags, title, isAlpha);

        if (suggestions.isEmpty()) {
            event.getHook().editOriginal("Found no suggestions matching the specified filters!").queue();
            return;
        }

        event.getHook().editOriginalEmbeds(buildSuggestionsEmbed(suggestions, tags, title, isAlpha, pageNum, true, true).build()).queue();
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

    public static EmbedBuilder buildSuggestionsEmbed(List<SuggestionCache.Suggestion> suggestions, String tags, String title, boolean alpha, int pageNum, boolean showNames, boolean showStats) {
        List<SuggestionCache.Suggestion> pages = InfoCommands.getPage(suggestions, pageNum, 10);
        int totalPages = (int) Math.ceil(suggestions.size() / 10.0);

        StringJoiner links = new StringJoiner("\n");
        double total = suggestions.size();
        double greenlit = suggestions.stream().filter(SuggestionCache.Suggestion::isGreenlit).count();
        String filters = (tags != null ? "- Filtered by tags: `" + tags + "`\n" : "") + (title != null ? "- Filtered by title: `" + title + "`\n" : "") + (alpha ? "- Filtered by Alpha: `Yes`" : "");

        for (SuggestionCache.Suggestion suggestion : pages) {
            String link = "[" + suggestion.getThreadName().replaceAll("`", "") + "](" + suggestion.getThread().getJumpUrl() + ")";
            link += (suggestion.isGreenlit() ? " " + getEmojiFormat(SuggestionConfig::getGreenlitEmojiId) : "") + "\n";
            link += suggestion.getThread().getAppliedTags().stream().map(ForumTag::getName).collect(Collectors.joining(", ")) + "\n";
            link = link.replaceAll("\n\n", "\n");

            if (showNames) {
                User user = NerdBotApp.getBot().getJDA().getUserById(suggestion.getThread().getOwnerIdLong());
                if (user != null) {
                    link += "Created by " + user.getEffectiveName() + "\n";
                }
            }

            link += getEmojiFormat(SuggestionConfig::getAgreeEmojiId) + " " + suggestion.getAgrees() + "\u3000" + getEmojiFormat(SuggestionConfig::getDisagreeEmojiId) + " " + suggestion.getDisagrees() + "\n";
            links.add(link);
        }

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Color.GREEN)
            .setTitle("Suggestions")
            .setDescription(links.toString());

        if (showStats) {
            embedBuilder.addField(
                    "Total",
                    String.valueOf((int) total),
                    true
                )
                .addField(
                    getEmojiFormat(SuggestionConfig::getGreenlitEmojiId),
                    (int) greenlit + " (" + (int) ((greenlit / total) * 100.0) + "%)",
                    true
                )
                .addBlankField(true);
        }

        embedBuilder.setFooter("Page: " + pageNum + "/" + totalPages);

        if (!filters.isEmpty()) {
            embedBuilder.addField("Filters", filters, false);
        }

        return embedBuilder;
    }

    private static String getEmojiFormat(Function<SuggestionConfig, String> emojiIdFunction) {
        return NerdBotApp.getBot().getJDA().getEmojiById(emojiIdFunction.apply(NerdBotApp.getBot().getConfig().getSuggestionConfig())).getFormatted();
    }

}
