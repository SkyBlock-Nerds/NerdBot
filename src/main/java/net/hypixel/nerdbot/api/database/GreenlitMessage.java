package net.hypixel.nerdbot.api.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.util.Util;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.awt.*;
import java.util.Date;
import java.util.List;

@AllArgsConstructor
@Builder
@Getter
@Setter
public class GreenlitMessage {

    private ObjectId id;
    private String userId, messageId, greenlitMessageId, suggestionTitle, suggestionContent, suggestionUrl, channelGroupName;
    private List<String> tags;
    private Date suggestionDate;
    private int agrees, disagrees;

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

    @BsonIgnore
    public EmbedBuilder getEmbed() {
        EmbedBuilder builder = new EmbedBuilder();

        User user = NerdBotApp.getBot().getJDA().getUserById(userId);
        if (user != null) {
            builder.setAuthor(user.getName(), suggestionUrl, user.getAvatarUrl());
            builder.setFooter("Suggested by " + user.getName(), null);
        } else {
            builder.setFooter("Suggested by an unknown user");
        }

        builder.setTitle(suggestionTitle, suggestionUrl);
        builder.setColor(Color.GREEN);
        builder.setDescription("Tags: `" + (tags.isEmpty() ? "N/A" : String.join(", ", tags)) + "`"
                + "\n\n"
                + suggestionContent
                + "\n\n"
        );

        builder.addField("Agrees", String.valueOf(agrees), true);
        builder.addField("Disagrees", String.valueOf(disagrees), true);
        builder.setTimestamp(suggestionDate.toInstant());

        return builder;
    }

}
