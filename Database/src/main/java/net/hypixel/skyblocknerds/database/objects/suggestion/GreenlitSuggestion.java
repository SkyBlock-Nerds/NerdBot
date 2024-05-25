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

    /**
     * Overridden lombok method that initializes the tags list if it is null
     *
     * @return a {@link List} of tags
     */
    public List<String> getTags() {
        if (tags == null) {
            tags = new ArrayList<>();
        }
        return tags;
    }

    /**
     * Returns whether the suggestion contains the "Greenlit" tag
     *
     * @return true if the suggestion is greenlit
     */
    public boolean isGreenlit() {
        return getTag("Greenlit").findAny().isPresent();
    }

    /**
     * Returns whether the suggestion contains the "Reviewed" tag
     *
     * @return true if the suggestion is reviewed
     */
    public boolean isReviewed() {
        return getTag("Reviewed").findAny().isPresent();
    }

    /**
     * Returns a {@link Stream} of tags that match the given tag
     *
     * @param tag the tag to match
     *
     * @return a {@link Stream} containing matching tags, if any
     */
    private Stream<String> getTag(String tag) {
        return getTags().stream().map(String::toLowerCase).filter(s -> s.equalsIgnoreCase(tag));
    }
}

