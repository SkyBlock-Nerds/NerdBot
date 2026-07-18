package net.hypixel.nerdbot.app.feature;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.app.storage.RepositoryDiscordUserStore;
import net.hypixel.nerdbot.app.util.HttpUtils;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.api.feature.SchedulableFeature;
import net.hypixel.nerdbot.discord.config.NerdBotConfig;
import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;
import net.hypixel.nerdbot.discord.util.DiscordUtils;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

        Result<MojangProfile, HttpException> profileResult = HttpUtils.getMojangProfile(existingProfile.getUniqueId());
        if (profileResult.isFailure()) {
            log.warn("Failed to fetch Mojang profile for {} (UUID: {})", discordUser.getDiscordId(), existingProfile.getUniqueId());
            return;
        }

        MojangProfile mojangProfile = profileResult.orElseGet(MojangProfile::new);

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

        String desiredNickname = mojangProfile.getUsername();

        if (needsNicknameUpdate(member.getEffectiveName(), desiredNickname)) {
            try {
                member.modifyNickname(desiredNickname).queue(
                    success -> log.info("Updated nickname for {} to {}", discordUser.getDiscordId(), desiredNickname),
                    throwable -> log.error("Failed to queue nickname update for {} to {}", discordUser.getDiscordId(), desiredNickname, throwable)
                );
            } catch (HierarchyException exception) {
                log.warn("Unable to modify the nickname of {} ({}) to {} due to a hierarchy exception", member.getUser().getName(), member.getId(), mojangProfile.getUsername());
            }
        }
    }

    /**
     * Select the users whose stored Mojang profile is assigned and stale enough to warrant a
     * refresh.
     *
     * <p>Extracted from {@link #executeTask()} so the selection can be exercised against an
     * in-memory {@link DiscordUserStore} without a live bot or database. The actual refresh
     * ({@link #updateNickname(DiscordUser)}) still performs HTTP and JDA calls and is left to
     * {@code executeTask()}.
     *
     * @param store         the user store to scan
     * @param cacheTtlHours the Mojang username cache TTL in hours
     * @return the users due for a profile update, in store iteration order
     */
    static List<DiscordUser> profilesRequiringUpdate(DiscordUserStore store, long cacheTtlHours) {
        List<DiscordUser> due = new ArrayList<>();

        for (DiscordUser discordUser : store.getAll()) {
            if (discordUser.isProfileAssigned() && discordUser.getMojangProfile().requiresCacheUpdate(cacheTtlHours)) {
                due.add(discordUser);
            }
        }

        return due;
    }

    /**
     * @param currentNickname the member's current effective name
     * @param desiredUsername the Mojang username the nickname should reflect
     * @return {@code true} if the current name does not already contain the desired username
     * (case-insensitive), i.e. a nickname change is warranted
     */
    static boolean needsNicknameUpdate(String currentNickname, String desiredUsername) {
        return !currentNickname.toLowerCase(Locale.ROOT).contains(desiredUsername.toLowerCase(Locale.ROOT));
    }

    @Override
    public void onFeatureStart() {
    }

    @Override
    public void executeTask() {
        if (BotEnvironment.getBot().isReadOnly()) {
            log.info("Bot is in read-only mode, skipping profile update task!");
            return;
        }

        if (!SkyBlockNerdsBot.config().isMojangForceNicknameUpdate()) {
            log.info("Forcefully updating nicknames is currently disabled!");
            return;
        }

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUserStore store = new RepositoryDiscordUserStore(discordUserRepository);
        long cacheTtlHours = SkyBlockNerdsBot.config().getMojangUsernameCacheTTL();

        profilesRequiringUpdate(store, cacheTtlHours).forEach(ProfileUpdateFeature::updateNickname);
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
        stopScheduledTask();
    }
}
