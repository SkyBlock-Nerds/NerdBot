package net.hypixel.skyblocknerds.database.objects.suggestion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@AllArgsConstructor
@Builder
@Getter
@Setter
public class GreenlitSuggestion {

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

    public GreenlitSuggestion() {
    }

    public List<String> getTags() {
        if (tags == null) {
            tags = new ArrayList<>();
        }
        return tags;
    }

    public boolean isGreenlit() {
        return getTag("Greenlit").findAny().isPresent();
    }

    public boolean isReviewed() {
        return getTag("Reviewed").findAny().isPresent();
    }

    private Stream<String> getTag(String tag) {
        return getTags().stream().map(String::toLowerCase).filter(s -> s.equalsIgnoreCase(tag));
    }
}

