package net.hypixel.nerdbot.discord.storage.database.model.greenlit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;

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

    public boolean isGreenlit() {
        return getTags().contains("Greenlit");
    }

    public boolean isReviewed() {
        return getTags().contains("Reviewed");
    }
}