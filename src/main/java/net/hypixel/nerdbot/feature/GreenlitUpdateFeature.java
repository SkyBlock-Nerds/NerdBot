package net.hypixel.nerdbot.feature;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.bot.Bot;
import net.hypixel.nerdbot.api.database.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.feature.BotFeature;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public class GreenlitUpdateFeature extends BotFeature {

    @Override
    public void onStart() {
        Bot nerdBot = NerdBotApp.getBot();
        ForumChannel suggestions = nerdBot.getJDA().getForumChannelById(nerdBot.getConfig().getSuggestionForumId());
        if (suggestions == null) {
            log.error("Couldn't find the suggestion forum channel from the bot config!");
            return;
        }

        log.info("Found suggestion forum channel with ID " + suggestions.getId());
        log.info("Found " + suggestions.getThreadChannels().size() + " threads in the suggestion forum channel!");

        List<ThreadChannel> greenlitThreads = new ArrayList<>(suggestions.getThreadChannels().stream().filter(threadChannel -> threadChannel.getAppliedTags().stream().map(ForumTag::getName).toList().contains("Greenlit")).toList());
        List<ThreadChannel> archived = suggestions.retrieveArchivedPublicThreadChannels().complete();
        log.info("Found " + archived.size() + " archived threads in the suggestion forum channel!");
        greenlitThreads.addAll(archived.stream()
                .filter(threadChannel -> !greenlitThreads.contains(threadChannel))
                .filter(threadChannel -> threadChannel.getAppliedTags().stream().map(ForumTag::getName).toList().contains("Greenlit"))
                .toList());

        List<GreenlitMessage> greenlits = NerdBotApp.getBot().getDatabase().getCollection("greenlit_messages", GreenlitMessage.class).find().into(new ArrayList<>());
        log.info("Found " + greenlitThreads.size() + " greenlit threads in the suggestion forum channel!");
        log.info("Found " + greenlits.size() + " greenlit messages in the database!");

    }

    @Override
    public void onEnd() {

    }
}
