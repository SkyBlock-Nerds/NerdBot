package net.hypixel.skyblocknerds.discordbot.feature;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.hypixel.skyblocknerds.api.configuration.ConfigurationManager;
import net.hypixel.skyblocknerds.api.curator.configuration.CuratorConfiguration;
import net.hypixel.skyblocknerds.api.feature.Feature;
import net.hypixel.skyblocknerds.discordbot.DiscordBot;
import net.hypixel.skyblocknerds.discordbot.configuration.GuildConfiguration;
import net.hypixel.skyblocknerds.discordbot.curator.SuggestionCurator;

import java.util.concurrent.TimeUnit;

@Log4j2
public class AutomaticCuratorFeature extends Feature {

    public AutomaticCuratorFeature() {
        super(24, TimeUnit.HOURS);
    }

    @Override
    public void onFeatureStart() {
        GuildConfiguration guildConfiguration = ConfigurationManager.loadConfig(GuildConfiguration.class);
        CuratorConfiguration curatorConfiguration = ConfigurationManager.loadConfig(CuratorConfiguration.class);

        if (!curatorConfiguration.isEnabled()) {
            log.warn("The curator system is currently disabled, but we would have attempted to curate suggestions now!");
            return;
        }

        if (guildConfiguration.getSuggestionForumIds().isEmpty()) {
            log.warn("No suggestion forum IDs found in GuildConfiguration");
            return;
        }

        guildConfiguration.getSuggestionForumIds().forEach(s -> {
            ForumChannel forumChannel = DiscordBot.getJda().getForumChannelById(s);

            if (forumChannel == null) {
                log.error("ForumChannel not found for ID " + s + " that is listed in GuildConfiguration");
                return;
            }

            SuggestionCurator suggestionCurator = new SuggestionCurator();
            suggestionCurator.execute(forumChannel);
        });
    }

    @Override
    public void onFeatureEnd() {
        // Nothing to do here
    }
}
