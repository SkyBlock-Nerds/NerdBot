package net.hypixel.nerdbot.app.generation;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.context.GenerationFeedback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.Arrays;

/** Builds the image generator's context for a command: where its notices go and whether April Fools rendering is on. */
@Slf4j
public class DiscordGenerationContexts {

    private static final MonthDay APRIL_FIRST = MonthDay.of(4, 1);

    private final Provider<NerdBotConfig> config;
    private final Clock clock;

    @Inject
    public DiscordGenerationContexts(Provider<NerdBotConfig> config, Clock clock) {
        this.config = config;
        this.clock = clock;
    }

    /** Notices are sent as follow-ups, so the interaction must already be deferred. */
    public GenerationContext create(IReplyCallback interaction, boolean hidden) {
        GenerationFeedback feedback = (message, forceEphemeral) ->
            interaction.getHook().sendMessage(message).setEphemeral(forceEphemeral || hidden).queue();
        String channelId = interaction.getChannelId();

        return new GenerationContext(channelId, feedback, isAprilFoolsIn(channelId));
    }

    boolean isAprilFoolsIn(@Nullable String channelId) {
        NerdBotConfig current = config.get();
        String[] channelIds = current.getChannelConfig().getAprilFoolsGenChannelIds();

        if (channelId == null || channelIds == null || Arrays.stream(channelIds).noneMatch(channelId::equalsIgnoreCase)) {
            return false;
        }

        ZoneId zone = aprilFoolsZone(current.getFunConfig().getAprilFoolsTimezone());
        return MonthDay.now(clock.withZone(zone)).equals(APRIL_FIRST);
    }

    private static ZoneId aprilFoolsZone(@Nullable String configured) {
        if (configured == null || configured.isBlank()) {
            return ZoneId.systemDefault();
        }

        try {
            return ZoneId.of(configured);
        } catch (DateTimeException exception) {
            log.warn("aprilFoolsTimezone '{}' is not a time zone, so the system zone is used", configured);
            return ZoneId.systemDefault();
        }
    }
}
