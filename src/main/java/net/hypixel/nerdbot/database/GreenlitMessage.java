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

    public GreenlitMessage() {
    }

    public GreenlitMessage(String userId, String messageId, String suggestionContent, Date suggestionDate, String suggestionUrl) {
        this.userId = userId;
        this.messageId = messageId;
        this.suggestionContent = suggestionContent;
        this.suggestionDate = suggestionDate;
        this.suggestionUrl = suggestionUrl;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSuggestionContent() {
        return suggestionContent;
    }

    public void setSuggestionContent(String suggestionContent) {
        this.suggestionContent = suggestionContent;
    }

    public Date getSuggestionDate() {
        return suggestionDate;
    }

    public void setSuggestionDate(Date suggestionDate) {
        this.suggestionDate = suggestionDate;
    }

    public String getSuggestionUrl() {
        return suggestionUrl;
    }

    public void setSuggestionUrl(String suggestionUrl) {
        this.suggestionUrl = suggestionUrl;
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
