package net.hypixel.nerdbot.app.testsupport;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.suggestion.Suggestion;
import net.hypixel.nerdbot.app.suggestion.SuggestionFactory;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link SuggestionFactory} whose first-message fetch answers from a map instead of Discord, and
 * which records each build. Threads listed with {@link #failFor} fail to build.
 */
public class FakeSuggestionFactory extends SuggestionFactory {

    public final List<String> built = new CopyOnWriteArrayList<>();
    private final Map<String, Message> firstMessages = new ConcurrentHashMap<>();
    private final Set<String> failing = ConcurrentHashMap.newKeySet();

    public FakeSuggestionFactory(NerdBotConfig config) {
        super(() -> config);
    }

    public FakeSuggestionFactory firstMessage(String threadId, Message message) {
        firstMessages.put(threadId, message);
        return this;
    }

    public FakeSuggestionFactory failFor(String threadId) {
        failing.add(threadId);
        return this;
    }

    @Override
    public CompletableFuture<Suggestion> build(ThreadChannel thread, @Nullable Suggestion.ChannelType knownType) {
        built.add(thread.getId());
        if (failing.contains(thread.getId())) {
            return CompletableFuture.failedFuture(new IllegalStateException("Discord said no"));
        }
        return super.build(thread, knownType);
    }

    @Override
    public CompletableFuture<Optional<Message>> firstMessage(ThreadChannel thread) {
        return CompletableFuture.completedFuture(Optional.ofNullable(firstMessages.get(thread.getId())));
    }
}
