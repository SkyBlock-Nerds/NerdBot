package net.hypixel.skyblocknerds.api.cache.suggestion;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Getter
@Setter
@ToString
public class Suggestion {

    private String messageId;
    private String messageContent;
    private String authorId;
    private String startMessageId;
    private Channel channel;
    private Map<String, Integer> reactions;
    private List<String> postTags;
    private OffsetDateTime createdAt;

    @AllArgsConstructor
    @Getter
    public static class Channel {
        private final String channelId;
        private final String channelName;
    }
}
