package net.hypixel.nerdbot.api.database.greenlit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.util.Util;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.awt.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@AllArgsConstructor
@Builder
@Getter
@Setter
public class GreenlitMessage {

    private ObjectId id;
    private String userId, messageId, greenlitMessageId, suggestionTitle, suggestionContent, suggestionUrl, channelGroupName;
    private List<String> tags;
    private List<String> positiveVoterIds;
    private long suggestionTimestamp;
    private int agrees, disagrees, neutrals;
    private boolean alpha;

    public GreenlitMessage() {
    }

    public GreenlitMessage setSuggestionTitle(String suggestionTitle) {
        this.suggestionTitle = suggestionTitle.replaceAll(Util.SUGGESTION_TITLE_REGEX.pattern(), "");
        return this;
    }

    public GreenlitMessage setSuggestionContent(String suggestionContent) {
        if (tags != null) {
            for (String tag : tags) {
                suggestionContent = suggestionContent.replaceAll("\\[" + tag + "\\\\]", "");
            }
        }
        this.suggestionContent = suggestionContent;
        return this;
    }

    public boolean isDocced() {
        return tags.contains("Docced");
    }

    @BsonIgnore
    public EmbedBuilder getEmbed() {
        EmbedBuilder builder = new EmbedBuilder();

        builder.setTitle(this.suggestionTitle, this.suggestionUrl);
        builder.setColor(Color.GREEN);

        StringBuilder description = new StringBuilder();

        if (this.tags != null && !this.tags.isEmpty()) {
            description.append("**Tags:** `");
            String tagString = String.join("`, `", this.tags);
            description.append(tagString).append("`\n\n");
        }

        description.append(this.suggestionContent);

        builder.setDescription(description.toString());
        builder.addField("Agrees", String.valueOf(this.agrees), true);
        builder.addField("Disagrees", String.valueOf(this.disagrees), true);
        builder.addField("Neutrals", String.valueOf(this.neutrals), true);
        builder.addBlankField(true);
        builder.setTimestamp(Instant.ofEpochMilli(this.suggestionTimestamp));

        if (this.positiveVoterIds != null && !this.positiveVoterIds.isEmpty()) {
            builder.addField(
                "Pre-Greenlit Voters",
                this.positiveVoterIds.stream()
                    .map(userId -> ("<@" + userId + ">"))
                    .collect(Collectors.joining(", ")),
                false
            );
        }

        Guild guild = NerdBotApp.getBot().getJDA().getGuildById(NerdBotApp.getBot().getConfig().getGuildId());
        if (guild == null) {
            return builder;
        }

        if (userId != null) {
            CompletableFuture<Member> memberFuture = CompletableFuture.supplyAsync(() -> guild.retrieveMemberById(userId).complete());
            Member member = memberFuture.join();
            builder.setFooter("Suggested by: " + member.getUser().getName(), member.getUser().getEffectiveAvatarUrl());
        } else {
            builder.setFooter("Suggested by: Unknown User");
        }

        return builder;
    }

}
