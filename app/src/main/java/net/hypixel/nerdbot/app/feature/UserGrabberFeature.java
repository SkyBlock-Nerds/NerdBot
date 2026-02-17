package net.hypixel.nerdbot.app.feature;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.hypixel.nerdbot.app.badge.BadgeManager;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.badge.BadgeEntry;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.birthday.BirthdayData;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.generator.GeneratorHistory;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;
import net.hypixel.nerdbot.discord.util.DiscordUtils;

import java.util.ArrayList;

@Slf4j
public class UserGrabberFeature extends BotFeature {

    @Override
    public void onFeatureStart() {
        if (BotEnvironment.getBot().isReadOnly()) {
            log.error("Bot is in read-only mode, skipping user grabber task!");
            return;
        }

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            log.error("Can't initiate feature as the database is not connected!");
            return;
        }

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        Guild guild = DiscordUtils.getMainGuild();
        log.info("Grabbing users from guild '{}' (ID: {})", guild.getName(), guild.getId());

        guild.loadMembers(member -> {
                if (member.getUser().isBot()) {
                    return;
                }

                log.info("Found user '{}' (ID: {})", member.getEffectiveName(), member.getId());

                DiscordUser discordUser = discordUserRepository.findById(member.getId());
                if (discordUser == null) {
                    discordUser = new DiscordUser(member.getId(), new ArrayList<>(), new LastActivity(), new BirthdayData(), new MojangProfile(), new GeneratorHistory(), false);
                    log.info("Creating new DiscordUser for user '{}' (ID: {})", member.getEffectiveName(), member.getId());
                }

                if (discordUser.getLastActivity() == null) {
                    log.info("Last activity for user '{}' (ID: {}) was null, setting to default values", member.getEffectiveName(), member.getId());
                    discordUser.setLastActivity(new LastActivity());
                }

                if (discordUser.getBadges() == null) {
                    log.info("Badges for user '{}' (ID: {}) was null, setting to default values", member.getEffectiveName(), member.getId());
                    discordUser.setBadges(new ArrayList<>());
                }

                for (BadgeEntry s : discordUser.getBadges()) {
                    if (BadgeManager.getBadgeById(s.badgeId()) == null && BadgeManager.getTieredBadgeById(s.badgeId()) == null) {
                        log.error("Badge '{}' for user '{}' (ID: {}) was not found in the badge map, removing", s, member.getEffectiveName(), member.getId());
                    }
                }

                discordUser.getBadges().removeIf(badgeEntry -> BadgeManager.getBadgeById(badgeEntry.badgeId()) == null
                    && BadgeManager.getTieredBadgeById(badgeEntry.badgeId()) == null);
                discordUserRepository.cacheObject(discordUser);
            })
            .onSuccess(aVoid -> log.info("Finished grabbing users from guild '{}' (ID: {})", guild.getName(), guild.getId()))
            .onError(throwable -> log.error("Failed to grab users from guild '{}' (ID: {})", guild.getName(), guild.getId(), throwable));
    }

    @Override
    public void onFeatureEnd() {
    }
}
