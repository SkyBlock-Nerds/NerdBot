package net.hypixel.nerdbot.feature;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.curator.Curator;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.bot.config.suggestion.SuggestionConfig;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.curator.ForumChannelCurator;
import net.hypixel.nerdbot.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.repository.GreenlitMessageRepository;

import java.util.List;
import java.util.Optional;
import java.util.TimerTask;

@Log4j2
public class CurateFeature extends BotFeature {

    @Override
    public void onFeatureStart() {
        SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();

        if (suggestionConfig.getForumChannelId() == null) {
            log.info("Not starting CurateFeature as 'forumChannelId' could not be found in the configuration file!");
            return;
        }

        Optional<ForumChannel> forumChannel = ChannelCache.getForumChannelById(suggestionConfig.getForumChannelId());

        if (forumChannel.isEmpty()) {
            log.info("Not starting CurateFeature as 'forumChannel' could not be found using 'forumChannelId'!");
            return;
        }

        this.timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                NerdBotApp.EXECUTOR_SERVICE.execute(() -> {
                    Database database = NerdBotApp.getBot().getDatabase();
                    Curator<ForumChannel, ThreadChannel> forumChannelCurator = new ForumChannelCurator(NerdBotApp.getBot().isReadOnly());
                    ForumChannel channel = forumChannel.get();
                    log.info("Processing suggestion forum channel '" + channel.getName() + "' (ID: " + channel + ")");

                    List<GreenlitMessage> result = forumChannelCurator.curate(channel);
                    if (result.isEmpty()) {
                        log.info("No new suggestions were greenlit from ID " + channel.getId() + " this time!");
                    } else {
                        log.info("Greenlit " + result.size() + " new suggestions from ID " + channel.getId() + ". Took " + (forumChannelCurator.getEndTime() - forumChannelCurator.getStartTime()) + "ms!");
                    }

                    // Update Database
                    result.forEach(greenlitMessage -> {
                        GreenlitMessageRepository greenlitMessageRepository = database.getRepositoryManager().getRepository(GreenlitMessageRepository.class);
                        greenlitMessageRepository.cacheObject(greenlitMessage);
                        PrometheusMetrics.GREENLIT_SUGGESTION_LENGTH.labels(greenlitMessage.getMessageId(), String.valueOf(greenlitMessage.getSuggestionContent().length())).inc();
                    });
                    log.info("Inserted " + result.size() + " new greenlit messages into the database!");
                    PrometheusMetrics.TOTAL_GREENLIT_MESSAGES_AMOUNT.inc(result.size());
                });
            }
        }, 30_000L, NerdBotApp.getBot().getConfig().getInterval());
    }

    @Override
    public void onFeatureEnd() {
        this.timer.cancel();
    }
}
