package net.hypixel.nerdbot.api.database;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.util.Util;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.awt.*;
import java.util.Date;
import java.util.List;

public class GreenlitMessage {

    private ObjectId id;
    private String userId, messageId, greenlitMessageId, suggestionTitle, suggestionContent, suggestionUrl, channelGroupName;
    private List<String> tags;
    private Date suggestionDate;
    private int agrees, disagrees;

    public GreenlitMessage() {
    }

    public GreenlitMessage(ObjectId id, String userId, String messageId, String greenlitMessageId, String suggestionTitle, String suggestionContent, String suggestionUrl, String channelGroupName, List<String> tags, Date suggestionDate, int agrees, int disagrees) {
        this.id = id;
        this.userId = userId;
        this.messageId = messageId;
        this.greenlitMessageId = greenlitMessageId;
        this.suggestionTitle = suggestionTitle;
        this.suggestionContent = suggestionContent;
        this.suggestionUrl = suggestionUrl;
        this.channelGroupName = channelGroupName;
        this.tags = tags;
        this.suggestionDate = suggestionDate;
        this.agrees = agrees;
        this.disagrees = disagrees;
    }

    public ObjectId getId() {
        return id;
    }

    public GreenlitMessage setId(ObjectId id) {
        this.id = id;
        return this;
    }

    public String getUserId() {
        return userId;
    }

    public GreenlitMessage setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public String getMessageId() {
        return messageId;
    }

    public GreenlitMessage setMessageId(String messageId) {
        this.messageId = messageId;
        return this;
    }

    public String getGreenlitMessageId() {
        return greenlitMessageId;
    }

    public GreenlitMessage setGreenlitMessageId(String greenlitMessageId) {
        this.greenlitMessageId = greenlitMessageId;
        return this;
    }

    public String getSuggestionTitle() {
        return suggestionTitle;
    }

    public GreenlitMessage setSuggestionTitle(String suggestionTitle) {
        this.suggestionTitle = suggestionTitle.replaceAll(Util.SUGGESTION_TITLE_REGEX.pattern(), "");
        return this;
    }

    public String getSuggestionContent() {
        return suggestionContent;
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

    public List<String> getTags() {
        return tags;
    }

    public GreenlitMessage setTags(List<String> tags) {
        this.tags = tags;
        return this;
    }

    public Date getSuggestionDate() {
        return suggestionDate;
    }

    public GreenlitMessage setSuggestionDate(Date suggestionDate) {
        this.suggestionDate = suggestionDate;
        return this;
    }

    public String getSuggestionUrl() {
        return suggestionUrl;
    }

    public GreenlitMessage setSuggestionUrl(String suggestionUrl) {
        this.suggestionUrl = suggestionUrl;
        return this;
    }

    public int getAgrees() {
        return agrees;
    }

    public GreenlitMessage setAgrees(int agrees) {
        this.agrees = agrees;
        return this;
    }

    public int getDisagrees() {
        return disagrees;
    }

    public GreenlitMessage setDisagrees(int disagrees) {
        this.disagrees = disagrees;
        return this;
    }

    public String getChannelGroupName() {
        return channelGroupName;
    }

    public GreenlitMessage setChannelGroupName(String channelGroupName) {
        this.channelGroupName = channelGroupName;
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
                + suggestionContent);
        builder.setTimestamp(suggestionDate.toInstant());
        return builder;
    }

}
