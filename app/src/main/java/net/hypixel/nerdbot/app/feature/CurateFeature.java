package net.hypixel.nerdbot.app.feature;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.hypixel.nerdbot.app.curator.ForumChannelCurator;
import net.hypixel.nerdbot.app.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.api.feature.SchedulableFeature;
import net.hypixel.nerdbot.discord.cache.ChannelCache;
import net.hypixel.nerdbot.discord.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.config.suggestion.SuggestionConfig;
import net.hypixel.nerdbot.app.curator.Curator;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import net.hypixel.nerdbot.marmalade.storage.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.marmalade.storage.database.repository.GreenlitMessageRepository;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

import java.util.List;
import java.util.Optional;
import java.util.TimerTask;

@Slf4j
public class CurateFeature extends BotFeature implements SchedulableFeature {

    @Override
    public void onFeatureStart() {
    }

    @Override
    public TimerTask buildTask() {
        return new TimerTask() {
            @Override
            public void run() {
                SuggestionConfig suggestionConfig = DiscordBotEnvironment.getBot().getConfig().getSuggestionConfig();

                if (suggestionConfig.getForumChannelId() == null) {
                    log.info("Not running CurateFeature: forumChannelId missing in config");
                    return;
                }

                Optional<ForumChannel> forumChannel = ChannelCache.getForumChannelById(suggestionConfig.getForumChannelId());
                if (forumChannel.isEmpty()) {
                    log.info("Not running CurateFeature: forumChannel not found for configured forumChannelId");
                    return;
                }

                BotEnvironment.EXECUTOR_SERVICE.execute(() -> {
                    Database database = BotEnvironment.getBot().getDatabase();
                    Curator<ForumChannel, ThreadChannel> forumChannelCurator = new ForumChannelCurator(BotEnvironment.getBot().isReadOnly());
                    ForumChannel channel = forumChannel.get();
                    log.info("Processing suggestion forum channel '" + channel.getName() + "' (ID: " + channel + ")");

                    List<GreenlitMessage> result = forumChannelCurator.curate(channel);
                    if (result.isEmpty()) {
                        log.info("No new suggestions were greenlit from ID " + channel.getId() + " this time!");
                    } else {
                        log.info("Greenlit " + result.size() + " new suggestions from ID " + channel.getId() + ". Took " + (forumChannelCurator.getEndTime() - forumChannelCurator.getStartTime()) + "ms!");
                    }

                    result.forEach(greenlitMessage -> {
                        GreenlitMessageRepository greenlitMessageRepository = database.getRepositoryManager().getRepository(GreenlitMessageRepository.class);
                        greenlitMessageRepository.cacheObject(greenlitMessage);
                        PrometheusMetrics.GREENLIT_SUGGESTION_LENGTH.labels(greenlitMessage.getMessageId(), String.valueOf(greenlitMessage.getSuggestionContent().length())).inc();
                    });
                    log.info("Inserted " + result.size() + " new greenlit messages into the database!");
                    PrometheusMetrics.TOTAL_GREENLIT_MESSAGES_AMOUNT.inc(result.size());
                });
            }
        };
    }

    @Override
    public long defaultInitialDelayMs(NerdBotConfig config) {
        return 30_000L;
    }

    @Override
    public long defaultPeriodMs(NerdBotConfig config) {
        return config.getInterval();
    }

    @Override
    public void onFeatureEnd() {
        this.timer.cancel();
    }
}
