package net.hypixel.nerdbot.api.database.model.greenlit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.hypixel.nerdbot.util.Util;
import org.bson.types.ObjectId;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Builder
@Getter
@Setter
public class GreenlitMessage {

    private ObjectId id;
    private String userId;
    private String messageId;
    private String greenlitMessageId;
    private String suggestionTitle;
    private String suggestionContent;
    private String suggestionUrl;
    private String channelGroupName;
    private List<String> tags;
    private List<String> positiveVoterIds;
    private long suggestionTimestamp;
    private int agrees;
    private int disagrees;
    private int neutrals;

    public GreenlitMessage() {
    }

    public List<String> getTags() {
        if (tags == null) {
            tags = new ArrayList<>();
        }
        return tags;
    }

    public void setSuggestionTitle(String suggestionTitle) {
        this.suggestionTitle = suggestionTitle.replaceAll(Util.SUGGESTION_TITLE_REGEX.pattern(), "");
    }

    public void setSuggestionContent(String suggestionContent) {
        if (tags != null) {
            for (String tag : tags) {
                suggestionContent = suggestionContent.replaceAll("\\[" + tag + "\\\\]", "");
            }
        }
        this.suggestionContent = suggestionContent;
    }

    public boolean isGreenlit() {
        return getTags().contains("Greenlit");
    }

    public boolean isReviewed() {
        return getTags().contains("Reviewed");
    }

    public EmbedBuilder createEmbed() {
        Color color;
        EmbedBuilder embedBuilder = new EmbedBuilder()
            .setTitle(suggestionTitle)
            .setDescription(suggestionContent)
            .setFooter("Suggestion ID: " + messageId)
            .setTimestamp(Instant.ofEpochMilli(suggestionTimestamp))
            .addField("Agrees", String.valueOf(agrees), true)
            .addField("Disagrees", String.valueOf(disagrees), true)
            .addField("Neutrals", String.valueOf(neutrals), true)
            .addField("Tags", String.join(", ", tags), false)
            .addField("Suggestion URL", suggestionUrl, true);

        if (isReviewed()) {
            color = new Color(51, 153, 255);
        //} else if (isAlpha()) {
        //    color = new Color(255, 255, 153);
        } else {
            color = Color.GREEN;
        }

        embedBuilder.setColor(color);

        return embedBuilder;
    }
}
