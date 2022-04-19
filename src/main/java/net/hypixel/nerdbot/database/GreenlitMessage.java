package net.hypixel.nerdbot.database;

import org.bson.types.ObjectId;

import java.util.Date;

public class GreenlitMessage {

    private ObjectId id;

    private String userId;

    private String messageId;

    private String suggestionContent;

    private Date suggestionDate;

    private String suggestionUrl;

    private int originalAgrees;

    private int originalDisagrees;

    public GreenlitMessage() {
    }

    public GreenlitMessage(String userId, String messageId, String suggestionContent, Date suggestionDate, String suggestionUrl, int originalAgrees, int originalDisagrees) {
        this.userId = userId;
        this.messageId = messageId;
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

    @Override
    public String toString() {
        return "GreenlitMessage{" +
                "id=" + id +
                ", userId=" + userId +
                ", messageId=" + messageId +
                ", suggestionContent='" + suggestionContent + '\'' +
                ", suggestionDate='" + suggestionDate + '\'' +
                ", suggestionUrl='" + suggestionUrl + '\'' +
                '}';
    }
}
