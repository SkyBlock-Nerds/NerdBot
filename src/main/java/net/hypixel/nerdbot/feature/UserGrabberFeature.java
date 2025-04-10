package net.hypixel.nerdbot.feature;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.internalapi.badge.BadgeManager;
import net.hypixel.nerdbot.internalapi.database.model.user.DiscordUser;
import net.hypixel.nerdbot.internalapi.database.model.user.badge.BadgeEntry;
import net.hypixel.nerdbot.internalapi.database.model.user.history.GeneratorHistory;
import net.hypixel.nerdbot.internalapi.database.model.user.language.UserLanguage;
import net.hypixel.nerdbot.internalapi.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.internalapi.feature.BotFeature;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.Util;

import java.util.ArrayList;

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

                DiscordUser discordUser = discordUserRepository.findOrCreateById(member.getId());

                if (discordUser.getLastActivity() == null) {
                    log.info("Last activity for " + member.getEffectiveName() + " was null. Setting to default values!");
                    discordUser.setLastActivity(new LastActivity());
                }

                if (discordUser.getLanguage() == null) {
                    log.info("Setting language for " + member.getEffectiveName() + " to ENGLISH");
                    discordUser.setLanguage(UserLanguage.ENGLISH);
                }

                if (discordUser.getBadges() == null) {
                    log.info("Badges for " + member.getEffectiveName() + " was null. Setting to default values!");
                    discordUser.setBadges(new ArrayList<>());
                }

                if (discordUser.getGeneratorHistory() == null) {
                    log.info("Generator command history for " + member.getEffectiveName() + " was null. Setting to default values!");
                    discordUser.setGeneratorHistory(new GeneratorHistory());
                }

                for (BadgeEntry s : discordUser.getBadges()) {
                    if (BadgeManager.getBadgeById(s.getBadgeId()) == null && BadgeManager.getTieredBadgeById(s.getBadgeId()) == null) {
                        log.error("Badge '" + s + "' for " + member.getEffectiveName() + " was not found in the badge map! Removing...");
                    }
                }

                discordUser.getBadges().removeIf(badgeEntry -> BadgeManager.getBadgeById(badgeEntry.getBadgeId()) == null && BadgeManager.getTieredBadgeById(badgeEntry.getBadgeId()) == null);
                discordUserRepository.cacheObject(discordUser);
            })
            .onSuccess(aVoid -> log.info("Finished grabbing users from guild " + guild.getName()))
            .onError(throwable -> log.error("Failed to grab users from guild " + guild.getName(), throwable));
    }

    @Override
    public void onFeatureEnd() {
    }
}
