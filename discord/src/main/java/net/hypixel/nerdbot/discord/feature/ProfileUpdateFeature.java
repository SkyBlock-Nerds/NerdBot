package net.hypixel.nerdbot.discord.feature;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.hypixel.nerdbot.BotEnvironment;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.DiscordUtils;
import net.hypixel.nerdbot.util.HttpUtils;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.TimerTask;

@Slf4j
public class ProfileUpdateFeature extends BotFeature {

    public static void updateNickname(DiscordUser discordUser) {
        MojangProfile existingProfile = discordUser.getMojangProfile();
        if (existingProfile == null) {
            log.warn("Skipping nickname update for {} because no Mojang profile is stored", discordUser.getDiscordId());
            return;
        }

        if (existingProfile.getUniqueId() == null) {
            log.warn("Skipping nickname update for {} because stored Mojang profile is missing a UUID", discordUser.getDiscordId());
            return;
        }

        MojangProfile mojangProfile = HttpUtils.getMojangProfile(existingProfile.getUniqueId());
        if (mojangProfile.getUsername() == null || mojangProfile.getUsername().isBlank()) {
            log.warn("Skipping nickname update for {} (UUID: {}) because the Mojang username is missing",
                discordUser.getDiscordId(),
                mojangProfile.getUniqueId());
            return;
        }

        discordUser.setMojangProfile(mojangProfile);
        Guild guild = DiscordUtils.getMainGuild();
        Member member;
        try {
            member = guild.retrieveMemberById(discordUser.getDiscordId()).complete();
        } catch (ErrorResponseException exception) {
            log.warn("Failed to retrieve guild member {} for nickname update", discordUser.getDiscordId(), exception);
            return;
        }

        if (member == null) {
            log.warn("Could not find guild member for {}; skipping nickname update", discordUser.getDiscordId());
            return;
        }

        String currentNickname = member.getEffectiveName();
        String normalizedNickname = currentNickname.toLowerCase(Locale.ROOT);
        String desiredNickname = mojangProfile.getUsername();

        if (!normalizedNickname.contains(desiredNickname.toLowerCase(Locale.ROOT))) {
            try {
                member.modifyNickname(desiredNickname).queue(
                    success -> log.info("Updated nickname for {} to {}", discordUser.getDiscordId(), desiredNickname),
                    throwable -> log.error("Failed to queue nickname update for {} to {}", discordUser.getDiscordId(), desiredNickname, throwable)
                );
            } catch (HierarchyException exception) {
                log.error("Unable to modify the nickname of " + member.getUser().getName() + " (" + member.getId() + ") to " + mojangProfile.getUsername() + " due to a hierarchy exception!");
            }
        }
    }

    @Override
    public void onFeatureStart() {
        this.timer.scheduleAtFixedRate(
            new TimerTask() {
                @Override
                public void run() {
                    if (BotEnvironment.getBot().isReadOnly()) {
                        log.info("Bot is in read-only mode, skipping profile update task!");
                        return;
                    }

                    if (!BotEnvironment.getBot().getConfig().isMojangForceNicknameUpdate()) {
                        log.info("Forcefully updating nicknames is currently disabled!");
                        return;
                    }

                    DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
                    discordUserRepository.forEach(discordUser -> {
                        if (discordUser.isProfileAssigned() && discordUser.getMojangProfile().requiresCacheUpdate()) {
                            updateNickname(discordUser);
                        }
                    });
                }
            }, 0L, Duration.of(BotEnvironment.getBot().getConfig().getMojangUsernameCacheTTL(), ChronoUnit.HOURS).toMillis());
    }

    @Override
    public void onFeatureEnd() {
        this.timer.cancel();
    }
}