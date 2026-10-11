package net.hypixel.nerdbot.app.suggestion;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Builds {@link Suggestion}s, fetching each thread's first message without blocking. */
public class SuggestionFactory {

    private final Provider<NerdBotConfig> config;

    @Inject
    public SuggestionFactory(Provider<NerdBotConfig> config) {
        this.config = config;
    }

    /**
     * @param knownType the thread's suggestion type when the caller already knows it, otherwise null
     */
    public CompletableFuture<Suggestion> build(ThreadChannel thread, @Nullable Suggestion.ChannelType knownType) {
        NerdBotConfig current = config.get();
        Suggestion.ChannelType type = knownType != null ? knownType : SuggestionTypeResolver.getThreadSuggestionType(thread, current);
        return firstMessage(thread).thenApply(message -> Suggestion.of(thread, type, message.orElse(null), current));
    }

    public CompletableFuture<Optional<Message>> firstMessage(ThreadChannel thread) {
        return thread.getHistoryFromBeginning(1).submit()
            .thenApply(history -> history.isEmpty() ? Optional.empty() : Optional.of(history.getRetrievedHistory().getFirst()));
    }
}
