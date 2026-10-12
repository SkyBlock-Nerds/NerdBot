package net.hypixel.nerdbot.app.feature;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.curator.Curation;
import net.hypixel.nerdbot.app.curator.ForumChannelCurator;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.api.feature.SchedulableFeature;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;

import java.time.Duration;
import java.util.Optional;

/** Curates the main suggestion forum on a schedule. */
@Slf4j
public class CurateFeature extends BotFeature implements SchedulableFeature {

    /**
     * Fallback curate cadence, used when the feature's config entry omits {@code periodMs}.
     */
    private static final long DEFAULT_PERIOD_MS = Duration.ofHours(12).toMillis();

    private final ForumChannelCurator curator;
    private final Provider<NerdBotConfig> config;
    private final JDA jda;

    @Inject
    public CurateFeature(ForumChannelCurator curator, Provider<NerdBotConfig> config, JDA jda) {
        this.curator = curator;
        this.config = config;
        this.jda = jda;
    }

    @Override
    public void onFeatureStart() {
    }

    @Override
    public void executeTask() {
        String forumId = config.get().getSuggestionConfig().getForumChannelId();
        // JDA throws on an id that isn't a number, which a missing config value would be
        Optional<ForumChannel> forum = Optional.ofNullable(forumId)
            .filter(id -> id.matches("\\d+"))
            .map(jda::getForumChannelById);
        if (forum.isEmpty()) {
            log.warn("Not curating; the suggestion forum ({}) can't be found", forumId);
            return;
        }

        Curation curation = new Curation(false);
        if (curator.curate(forum.get(), curation)) {
            log.info("Greenlit {} suggestions in {}ms", curation.greenlit().size(), curation.durationMs());
        }
    }

    @Override
    public long defaultInitialDelayMs(DiscordBotConfig config) {
        return 30_000L;
    }

    @Override
    public long defaultPeriodMs(DiscordBotConfig config) {
        return DEFAULT_PERIOD_MS;
    }

    @Override
    public void onFeatureEnd() {
        stopScheduledTask();
    }
}
