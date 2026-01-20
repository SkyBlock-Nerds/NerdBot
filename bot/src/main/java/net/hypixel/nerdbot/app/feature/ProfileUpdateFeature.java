package net.hypixel.nerdbot.app.feature;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.app.util.HttpUtils;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.api.feature.SchedulableFeature;
import net.hypixel.nerdbot.discord.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.discord.storage.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.discord.storage.database.repository.DiscordUserRepository;
import net.hypixel.nerdbot.discord.util.DiscordUtils;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.TimerTask;

@Slf4j
public class ProfileUpdateFeature extends BotFeature implements SchedulableFeature {

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
    }

    @Override
    public TimerTask buildTask() {
        return new TimerTask() {
            @Override
            public void run() {
                if (BotEnvironment.getBot().isReadOnly()) {
                    log.info("Bot is in read-only mode, skipping profile update task!");
                    return;
                }

                if (!SkyBlockNerdsBot.config().isMojangForceNicknameUpdate()) {
                    log.info("Forcefully updating nicknames is currently disabled!");
                    return;
                }

                DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
                long cacheTtlHours = SkyBlockNerdsBot.config().getMojangUsernameCacheTTL();
                discordUserRepository.forEach(discordUser -> {
                    if (discordUser.isProfileAssigned() && discordUser.getMojangProfile().requiresCacheUpdate(cacheTtlHours)) {
                        updateNickname(discordUser);
                    }
                });
            }
        };
    }

    @Override
    public long defaultInitialDelayMs(NerdBotConfig config) {
        return 0L;
    }

    @Override
    public long defaultPeriodMs(NerdBotConfig config) {
        return Duration.of(config.getMojangUsernameCacheTTL(), ChronoUnit.HOURS).toMillis();
    }

    @Override
    public void onFeatureEnd() {
        this.timer.cancel();
    }
}
