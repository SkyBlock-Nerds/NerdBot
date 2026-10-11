package net.hypixel.nerdbot.app.testsupport;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Fake suggestion threads and first messages for the suggestion cache and command tests. */
public final class SuggestionFixtures {

    public static final String GUILD_ID = "1";
    public static final String AGREE = "900";
    public static final String DISAGREE = "901";
    public static final String NEUTRAL = "902";
    public static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-11T12:00:00Z");

    private SuggestionFixtures() {
    }

    /** A config whose emoji ids match {@link #reaction}'s, with the given suggestion forum. */
    public static NerdBotConfig config(String suggestionForumId) {
        NerdBotConfig config = new NerdBotConfig();
        config.getSuggestionConfig().setForumChannelId(suggestionForumId);
        config.getSuggestionConfig().setGreenlitTag("Greenlit");
        config.getSuggestionConfig().setReviewedTag("Reviewed");
        config.getEmojiConfig().setAgreeEmojiId(AGREE);
        config.getEmojiConfig().setDisagreeEmojiId(DISAGREE);
        config.getEmojiConfig().setNeutralEmojiId(NEUTRAL);
        return config;
    }

    public static ForumTag tag(String name) {
        return Fakes.of(ForumTag.class, Map.of("getName", name));
    }

    /**
     * A thread whose calls, including its manager's and the messages sent to it, are recorded on
     * {@code recorder}. Manager updates succeed unless {@code managerSucceeds} is false.
     */
    public static ThreadChannel thread(CallRecorder recorder, long id, String forumId, OffsetDateTime created,
                                       boolean archived, boolean locked, List<ForumTag> tags, boolean managerSucceeds) {
        CallRecorder.Answer queue = args -> {
            if (managerSucceeds && args.length > 0 && args[0] != null) {
                consumer(args[0]).accept(null);
            } else if (!managerSucceeds && args.length > 1 && args[1] != null) {
                consumer(args[1]).accept(new IllegalStateException("missing permission"));
            }
            return null;
        };
        CompletableFuture<Object> submitted = managerSucceeds
            ? CompletableFuture.completedFuture(null)
            : CompletableFuture.failedFuture(new IllegalStateException("missing permission"));

        Map<String, Object> answers = new HashMap<>();
        // sendMessage(String) is a default method that builds JDA's own action class, which needs a real JDA
        answers.put("sendMessage", (CallRecorder.Answer) args -> recorder.fake(MessageCreateAction.class));
        answers.put("getId", String.valueOf(id));
        answers.put("getIdLong", id);
        answers.put("getName", "Suggestion " + id);
        answers.put("getOwnerId", "500");
        answers.put("getOwnerIdLong", 500L);
        answers.put("getGuild", Fakes.of(Guild.class, Map.of("getId", GUILD_ID)));
        answers.put("getTimeCreated", created);
        ForumChannel forum = Fakes.of(ForumChannel.class, Map.of("getId", forumId, "getName", "forum " + forumId,
            "getAvailableTags", List.of(tag("Greenlit"), tag("Reviewed"))));
        answers.put("getParentChannel", Fakes.of(IThreadContainerUnion.class, Map.of("getId", forumId, "asForumChannel", forum)));
        answers.put("getAppliedTags", tags);
        answers.put("getLatestMessageIdLong", 0L);
        answers.put("isArchived", archived);
        answers.put("isLocked", locked);
        answers.put("getManager", recorder.fake(ThreadChannelManager.class, Map.of("queue", queue, "submit", submitted)));
        return recorder.fake(ThreadChannel.class, answers);
    }

    public static ThreadChannel thread(CallRecorder recorder, long id, String forumId, OffsetDateTime created,
                                       boolean archived, boolean locked, List<ForumTag> tags) {
        return thread(recorder, id, forumId, created, archived, locked, tags, true);
    }

    public static ThreadChannel thread(CallRecorder recorder, long id, String forumId) {
        return thread(recorder, id, forumId, NOW, false, false, List.of());
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Object> consumer(Object value) {
        return (Consumer<Object>) value;
    }

    public static Message firstMessage(long id, long authorId, OffsetDateTime created, MessageReaction... reactions) {
        return Fakes.of(Message.class, Map.of(
            "getId", String.valueOf(id),
            "getIdLong", id,
            "getAuthor", Fakes.of(User.class, Map.of("getId", String.valueOf(authorId), "getIdLong", authorId)),
            "getTimeCreated", created,
            "getReactions", List.of(reactions),
            "getJumpUrl", "https://discord.com/channels/1/" + id,
            "getContentRaw", "Please add this"));
    }

    public static MessageReaction reaction(String emojiId, int count) {
        EmojiUnion emoji = (EmojiUnion) Emoji.fromFormatted("<:vote:" + emojiId + ">");
        return new MessageReaction(null, null, emoji, 0L, 0L, new boolean[]{false, false}, new int[]{count, 0});
    }
}
