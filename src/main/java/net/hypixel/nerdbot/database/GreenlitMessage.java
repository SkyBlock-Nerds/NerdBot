package net.hypixel.nerdbot.database;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.channel.Reactions;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.awt.*;
import java.util.Date;

public class GreenlitMessage {

    private ObjectId id;

    private String userId;

    private String messageId;

    private String suggestionTitle;

    private String suggestionContent;

    private Date suggestionDate;

    private String suggestionUrl;

    private int originalAgrees;

    private int originalDisagrees;

    public GreenlitMessage() {
    }

    public GreenlitMessage(ObjectId id, String userId, String messageId, String suggestionTitle, String suggestionContent, Date suggestionDate, String suggestionUrl, int originalAgrees, int originalDisagrees) {
        this.id = id;
        this.userId = userId;
        this.messageId = messageId;
        this.suggestionTitle = suggestionTitle;
        this.suggestionContent = suggestionContent;
        this.suggestionDate = suggestionDate;
        this.suggestionUrl = suggestionUrl;
        this.originalAgrees = originalAgrees;
        this.originalDisagrees = originalDisagrees;
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

    public String getSuggestionTitle() {
        return suggestionTitle;
    }

    public GreenlitMessage setSuggestionTitle(String suggestionTitle) {
        this.suggestionTitle = suggestionTitle;
        return this;
    }

    public String getSuggestionContent() {
        return suggestionContent;
    }

    public GreenlitMessage setSuggestionContent(String suggestionContent) {
        this.suggestionContent = suggestionContent;
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

    public int getOriginalAgrees() {
        return originalAgrees;
    }

    public GreenlitMessage setOriginalAgrees(int originalAgrees) {
        this.originalAgrees = originalAgrees;
        return this;
    }

    public int getOriginalDisagrees() {
        return originalDisagrees;
    }

    public GreenlitMessage setOriginalDisagrees(int originalDisagrees) {
        this.originalDisagrees = originalDisagrees;
        return this;
    }

    @BsonIgnore
    public EmbedBuilder getEmbed() {
        EmbedBuilder builder = new EmbedBuilder();
        User user = NerdBotApp.getBot().getJDA().getUserById(userId);
        builder.setAuthor(user.getName(), suggestionUrl, user.getAvatarUrl());
        builder.setTitle(suggestionTitle, suggestionUrl);
        builder.setColor(Color.GREEN);
        builder.setDescription(suggestionContent + "\n\n" + Reactions.THUMBS_UP_EMOJI + " " + originalAgrees + " " + Reactions.THUMBS_DOWN_EMOJI + " " + originalDisagrees);
        builder.setFooter("Suggested by " + user.getName(), null);
        builder.setTimestamp(suggestionDate.toInstant());
        return builder;
    }
}
