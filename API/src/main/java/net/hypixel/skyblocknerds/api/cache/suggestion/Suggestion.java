package net.hypixel.skyblocknerds.api.cache.suggestion;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.List;

@AllArgsConstructor
@Getter
@Setter
@ToString
public class Suggestion {

    private String messageId;
    private String threadTitle;
    private String messageContent;
    private String authorId;
    private String startMessageId;
    private Channel channel;
    private List<Reaction> reactions;
    private List<String> postTags;
    private OffsetDateTime createdAt;

    /**
     * Check if the suggestion is greenlit.
     *
     * @return {@code true} if the suggestion is greenlit.
     */
    public boolean isGreenlit() {
        return postTags.contains("greenlit");
    }

    /**
     * Check if the suggestion is reviewed.
     *
     * @return {@code true} if the suggestion is reviewed.
     */
    public boolean isReviewed() {
        return postTags.contains("reviewed");
    }

    @AllArgsConstructor
    @Getter
    public static class Channel {
        private final String channelId;
        private final String channelName;
    }

    @AllArgsConstructor
    @Getter
    @ToString
    public static class Reaction {
        private final String emojiId;
        private final String emojiName;
        private final int count;
    }
}
