package net.hypixel.nerdbot.feature;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.BirthdayData;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.Util;

@Log4j2
public class UserGrabberFeature extends BotFeature {

    @Override
    public void onFeatureStart() {
        if (NerdBotApp.getBot().isReadOnly()) {
            log.error("Bot is in read-only mode, skipping user grabber task!");
            return;
        }

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            log.error("Can't initiate feature as the database is not connected!");
            return;
        }

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        Guild guild = Util.getMainGuild();
        log.info("Grabbing users from guild " + guild.getName());

        guild.loadMembers(member -> {
                if (member.getUser().isBot()) {
                    return;
                }

                DiscordUser discordUser = discordUserRepository.findById(member.getId());
                if (discordUser == null) {
                    discordUser = new DiscordUser(member.getId(), new LastActivity(), new BirthdayData(), new MojangProfile());
                    log.info("Creating new DiscordUser for user " + member.getId());
                }

                if (discordUser.getLastActivity() == null) {
                    log.info("Last activity for " + member.getEffectiveName() + " was null. Setting to default values!");
                    discordUser.setLastActivity(new LastActivity());
                }

                discordUserRepository.cacheObject(discordUser);
            })
            .onSuccess(aVoid -> log.info("Finished grabbing users from guild " + guild.getName()))
            .onError(throwable -> log.error("Failed to grab users from guild " + guild.getName(), throwable));
    }

    @Override
    public void onFeatureEnd() {
    }
}
