package net.hypixel.skyblocknerds.discordbot.curator;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.hypixel.skyblocknerds.api.curator.Curator;
import net.hypixel.skyblocknerds.database.objects.suggestion.GreenlitSuggestion;
import net.hypixel.skyblocknerds.discordbot.DiscordBot;
import net.hypixel.skyblocknerds.utilities.StringUtilities;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public class SuggestionCurator extends Curator<ForumChannel, GreenlitSuggestion> {

    public SuggestionCurator() {
        super(DiscordBot.getCommandLine().hasOption("readOnly"));
    }

    @Override
    public void execute(ForumChannel forumChannel) {
        List<ThreadChannel> threadChannels = new ArrayList<>(forumChannel.getThreadChannels());
        forumChannel.retrieveArchivedPublicThreadChannels().stream()
                .dropWhile(threadChannels::contains)
                .forEach(threadChannel -> {
                    log.info("Found thread channel " + threadChannel.getName());
                    threadChannels.add(threadChannel);
                });

        log.info("Found " + threadChannels.size() + " thread channels in forum channel " + StringUtilities.formatNameWithId(forumChannel.getName(), forumChannel.getId()));

        threadChannels.forEach(threadChannel -> {

        });
    }
}
