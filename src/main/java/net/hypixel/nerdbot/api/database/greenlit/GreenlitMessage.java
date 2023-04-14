package net.hypixel.nerdbot.api.database.greenlit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.util.Util;
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
    private boolean alpha;

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

    public boolean isDocced() {
        return getTags().contains("Docced");
    }
}
