package net.hypixel.nerdbot.feature;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.Util;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.TimerTask;

@Log4j2
public class ProfileUpdateFeature extends BotFeature {

    public static void updateNickname(DiscordUser discordUser) {
        MojangProfile mojangProfile = Util.getMojangProfile(discordUser.getMojangProfile().getUniqueId());
        discordUser.setMojangProfile(mojangProfile);
        Guild guild = Util.getMainGuild();
        Member member = guild.retrieveMemberById(discordUser.getDiscordId()).complete();

        if (!member.getEffectiveName().toLowerCase().contains(mojangProfile.getUsername().toLowerCase())) {
            try {
                member.modifyNickname(mojangProfile.getUsername()).queue();
            } catch (HierarchyException exception) {
                log.error("Unable to modify the nickname of " + member.getUser().getName() + " (" + member.getEffectiveName() + ") [" + member.getId() + "]", exception);
            }
        }
    }

    @Override
    public void onFeatureStart() {
        this.timer.scheduleAtFixedRate(
            new TimerTask() {
                @Override
                public void run() {
                    if (NerdBotApp.getBot().isReadOnly()) {
                        log.info("Bot is in read-only mode, skipping profile update task!");
                        return;
                    }

                    if (!NerdBotApp.getBot().getConfig().isMojangForceNicknameUpdate()) {
                        log.info("Forcefully updating nicknames is currently disabled!");
                        return;
                    }

                    DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
                    discordUserRepository.forEach(discordUser -> {
                        if (discordUser.isProfileAssigned() && discordUser.getMojangProfile().requiresCacheUpdate()) {
                            updateNickname(discordUser);
                        }
                    });
                }
            }, 0L, Duration.of(NerdBotApp.getBot().getConfig().getMojangUsernameCacheTTL(), ChronoUnit.HOURS).toMillis());
    }

    @Override
    public void onFeatureEnd() {
        this.timer.cancel();
    }
}
