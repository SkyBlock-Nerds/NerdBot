package net.hypixel.nerdbot.util.discord;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.util.Util;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Log4j2
public class SuggestionCache {

    private static final List<String> GREENLIT_TAGS = Arrays.asList("greenlit", "docced");
    private final Cache<String, Suggestion> cache = Caffeine.newBuilder()
        .scheduler(Scheduler.systemScheduler())
        .refreshAfterWrite(60, TimeUnit.MINUTES)
        .softValues()
        .build(id -> new Suggestion(NerdBotApp.getBot().getJDA().getThreadChannelById(id)));

    public SuggestionCache() {
        Stream<ThreadChannel> channels = Util.concatStreams(NerdBotApp.getBot().getConfig().getSuggestionForumIds(), NerdBotApp.getBot().getConfig().getAlphaSuggestionForumIds())
            .map(NerdBotApp.getBot().getJDA()::getForumChannelById)
            .filter(Objects::nonNull)
            .flatMap(forumChannel -> Stream.concat(forumChannel.getThreadChannels().stream(), forumChannel.retrieveArchivedPublicThreadChannels().stream()))
            .filter(thread -> thread.getAppliedTags().stream().map(tag -> tag.getName().toLowerCase()).anyMatch(GREENLIT_TAGS::contains));

        channels.forEach(this::addSuggestion);
    }

    public void addSuggestion(ThreadChannel threadChannel) {
        this.cache.put(threadChannel.getId(), new Suggestion(threadChannel));
        log.info("Added suggestion '" + threadChannel.getName() + "' (ID: " + threadChannel.getId() + ") to the suggestion cache!");
    }

    public Suggestion getSuggestion(String id) {
        return this.cache.getIfPresent(id);
    }

    public List<Suggestion> getSuggestions() {
        return this.cache.asMap()
            .values()
            .stream()
            .sorted((o1, o2) -> Long.compare(o2.getThread().getTimeCreated().toInstant().toEpochMilli(), o1.getThread().getTimeCreated().toInstant().toEpochMilli()))
            .toList();
    }

    public void removeSuggestion(ThreadChannel threadChannel) {
        this.cache.invalidate(threadChannel.getId());
        log.info("Removed suggestion '" + threadChannel.getName() + "' (ID: " + threadChannel.getId() + ") from the suggestion cache!");
    }

    public static class Suggestion {

        @Getter
        private final ThreadChannel thread;
        @Getter
        private final String parentId;
        @Getter
        private final boolean alpha;
        @Getter
        private final int agrees;
        @Getter
        private final int disagrees;
        @Getter
        private final boolean greenlit;
        @Getter
        private final boolean deleted;

        public Suggestion(ThreadChannel thread) {
            this.thread = thread;
            this.parentId = thread.getParentChannel().asForumChannel().getId();

            if (NerdBotApp.getBot().getConfig().getAlphaSuggestionForumIds() != null) {
                this.alpha = Arrays.stream(NerdBotApp.getBot().getConfig().getAlphaSuggestionForumIds()).anyMatch(this.parentId::equalsIgnoreCase);
            } else {
                this.alpha = thread.getName().toLowerCase().contains("alpha");
            }

            MessageHistory history = thread.getHistoryFromBeginning(1).complete();
            Message message = history.getRetrievedHistory().get(0);

            this.deleted = message == null;
            this.agrees = this.deleted ? 0 : getReactionCount(message, NerdBotApp.getBot().getConfig().getEmojiConfig().getAgreeEmojiId());
            this.disagrees = this.deleted ? 0 : getReactionCount(message, NerdBotApp.getBot().getConfig().getEmojiConfig().getDisagreeEmojiId());
            this.greenlit = thread.getAppliedTags().stream().anyMatch(forumTag -> GREENLIT_TAGS.contains(forumTag.getName().toLowerCase()));
        }

        public static int getReactionCount(Message message, String emojiId) {
            return message.getReactions().stream()
                .filter(reaction -> reaction.getEmoji().getType() == Emoji.Type.CUSTOM)
                .filter(reaction -> reaction.getEmoji().asCustom().getId().equalsIgnoreCase(emojiId))
                .mapToInt(MessageReaction::getCount)
                .findFirst()
                .orElse(0);
        }

        public boolean notDeleted() {
            return !this.isDeleted();
        }
    }

}
