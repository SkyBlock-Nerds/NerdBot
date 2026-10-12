package net.hypixel.nerdbot.app.listener;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.app.config.FunConfig;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.util.DiscordUtils;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Month;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** Reacts to configured members' messages, and on April Fools' Day to anyone's by chance. */
@Slf4j
public class FunListener {

    private static final MonthDay APRIL_FOOLS = MonthDay.of(Month.APRIL, 1);

    private final GuildScope guildScope;
    private final Provider<NerdBotConfig> config;
    private final Clock clock;
    // Parsed once per configured value, so a bad zone is reported once rather than on every message
    private volatile ConfiguredZone zone = new ConfiguredZone(null, ZoneId.systemDefault());

    private record ConfiguredZone(String configured, ZoneId zoneId) {
    }

    @Inject
    public FunListener(GuildScope guildScope, Provider<NerdBotConfig> config, Clock clock) {
        this.guildScope = guildScope;
        this.config = config;
        this.clock = clock;
    }

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.isFromGuild() && guildScope.isMainGuild(event.getGuild()) && !event.getAuthor().isBot() && !event.isWebhookMessage()) {
            react(event.getMessage(), event.getAuthor().getId());
        }
    }

    void react(Message message, String authorId) {
        FunConfig funConfig = config.get().getFunConfig();

        if (funConfig.getAutoReactions() != null) {
            String emoji = funConfig.getAutoReactions().get(authorId);
            if (emoji != null) {
                addReaction(message, emoji);
            }
        }

        if (funConfig.getAprilFoolsReactionChance() != null && funConfig.getAprilFoolsReactionEmoji() != null
            && isAprilFools(funConfig.getAprilFoolsTimezone())
            && ThreadLocalRandom.current().nextDouble() < funConfig.getAprilFoolsReactionChance()) {
            addReaction(message, funConfig.getAprilFoolsReactionEmoji());
        }
    }

    private void addReaction(Message message, String emoji) {
        DiscordUtils.getEmoji(emoji).ifPresentOrElse(
            found -> message.addReaction(found).queue(null, error -> log.debug("Failed to react to message {}", message.getId(), error)),
            () -> log.debug("Fun reaction '{}' isn't an emoji the bot can use", emoji));
    }

    boolean isAprilFools(String configuredZone) {
        return MonthDay.from(clock.instant().atZone(zoneFor(configuredZone))).equals(APRIL_FOOLS);
    }

    private ZoneId zoneFor(String configured) {
        ConfiguredZone current = zone;
        if (Objects.equals(current.configured(), configured)) {
            return current.zoneId();
        }

        ZoneId parsed = ZoneId.systemDefault();
        if (configured != null) {
            try {
                parsed = ZoneId.of(configured);
            } catch (DateTimeException exception) {
                log.warn("April Fools' timezone '{}' isn't valid, so the system zone {} is used", configured, parsed);
            }
        }

        zone = new ConfiguredZone(configured, parsed);
        return parsed;
    }
}
