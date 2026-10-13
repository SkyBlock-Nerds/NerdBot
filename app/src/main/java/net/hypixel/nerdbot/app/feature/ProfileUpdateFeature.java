package net.hypixel.nerdbot.app.feature;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.inject.BotState;
import net.hypixel.nerdbot.app.listener.GuildScope;
import net.hypixel.nerdbot.app.profile.MinecraftAccounts;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.app.user.MemberQueue;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.api.feature.SchedulableFeature;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;
import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Refreshes linked members' Minecraft names once their stored profile is older than
 * {@code mojangUsernameCacheTTL} hours, saves them, and sets each member's nickname to their
 * Minecraft name. One member failing doesn't stop the rest.
 */
@Slf4j
public class ProfileUpdateFeature extends BotFeature implements SchedulableFeature {

    // A cache TTL of zero would otherwise schedule the refresh to run constantly
    private static final Duration MINIMUM_PERIOD = Duration.ofHours(1);

    enum Outcome {
        REFRESHED,
        FAILED,
        SKIPPED
    }

    private final DiscordUserStore users;
    private final MemberQueue queue;
    private final MinecraftAccounts accounts;
    private final GuildScope guildScope;
    private final JDA jda;
    private final Provider<NerdBotConfig> config;
    private final Provider<BotState> state;

    @Inject
    public ProfileUpdateFeature(DiscordUserStore users, MemberQueue queue, MinecraftAccounts accounts, GuildScope guildScope, JDA jda,
                                Provider<NerdBotConfig> config, Provider<BotState> state) {
        this.users = users;
        this.queue = queue;
        this.accounts = accounts;
        this.guildScope = guildScope;
        this.jda = jda;
        this.config = config;
        this.state = state;
    }

    @Override
    public void onFeatureStart() {
    }

    @Override
    public void executeTask() {
        if (state.get().isReadOnly()) {
            log.info("Not refreshing Minecraft profiles; the bot is in read-only mode");
            return;
        }
        if (!config.get().isMojangForceNicknameUpdate()) {
            log.info("Not refreshing Minecraft profiles; forced nickname updates are off");
            return;
        }

        Optional<Guild> guild = guildScope.mainGuild(jda);
        if (guild.isEmpty()) {
            log.warn("Not refreshing Minecraft profiles; the bot isn't in the configured guild");
            return;
        }

        long ttlHours = config.get().getMojangUsernameCacheTTL();
        int refreshed = 0;
        int failed = 0;
        for (String memberId : users.findAllIds()) {
            Outcome outcome = refresh(memberId, ttlHours, guild.get());
            if (outcome == Outcome.REFRESHED) {
                refreshed++;
            } else if (outcome == Outcome.FAILED) {
                failed++;
            }
        }
        log.info("Refreshed {} Minecraft profiles; {} couldn't be refreshed", refreshed, failed);
    }

    Outcome refresh(String memberId, long ttlHours, Guild guild) {
        try {
            Optional<DiscordUser> stored = users.findById(memberId);
            if (stored.isEmpty() || !isDue(stored.get(), ttlHours)) {
                return Outcome.SKIPPED;
            }

            MojangProfile current = stored.get().getMojangProfile();
            Result<MojangProfile, HttpException> fetched = accounts.mojangProfile(current.getUniqueId().toString()).join();
            Optional<MojangProfile> profile = fetched.toOptional().filter(found -> found.getUsername() != null && !found.getUsername().isBlank());
            if (profile.isEmpty()) {
                log.warn("Couldn't refresh the Minecraft profile of member {} ({})", memberId, current.getUniqueId());
                return Outcome.FAILED;
            }

            queue.withRecord(memberId, "profile refresh", user -> {
                user.setMojangProfile(profile.get());
                users.save(user).join();
            }).join();
            try {
                updateNickname(guild, memberId, profile.get().getUsername());
            } catch (RuntimeException exception) {
                log.warn("Refreshed the Minecraft profile of member {} but couldn't update their nickname", memberId, exception);
            }
            return Outcome.REFRESHED;
        } catch (RuntimeException exception) {
            log.error("Failed to refresh the Minecraft profile of member {}", memberId, exception);
            return Outcome.FAILED;
        }
    }

    static boolean isDue(DiscordUser user, long ttlHours) {
        return user.isProfileAssigned() && user.getMojangProfile().requiresCacheUpdate(ttlHours);
    }

    private static void updateNickname(Guild guild, String memberId, String minecraftName) {
        Member member;
        try {
            member = guild.getMemberById(memberId) != null ? guild.getMemberById(memberId) : guild.retrieveMemberById(memberId).complete();
        } catch (ErrorResponseException exception) {
            log.debug("Not renaming member {}; they're no longer in the guild", memberId);
            return;
        }

        if (!needsNicknameUpdate(member.getEffectiveName(), minecraftName)) {
            return;
        }

        try {
            member.modifyNickname(minecraftName).queue(
                done -> log.info("Renamed member {} to {}", memberId, minecraftName),
                error -> log.warn("Failed to rename member {} to {}", memberId, minecraftName, error));
        } catch (PermissionException exception) {
            log.warn("Can't rename member {} to {}: {}", memberId, minecraftName, exception.getMessage());
        }
    }

    /**
     * @return whether the current name doesn't already contain the Minecraft name, ignoring case
     */
    static boolean needsNicknameUpdate(String currentNickname, String desiredUsername) {
        return !currentNickname.toLowerCase(Locale.ROOT).contains(desiredUsername.toLowerCase(Locale.ROOT));
    }

    @Override
    public long defaultInitialDelayMs(DiscordBotConfig config) {
        return 0L;
    }

    @Override
    public long defaultPeriodMs(DiscordBotConfig config) {
        Duration ttl = Duration.ofHours(((NerdBotConfig) config).getMojangUsernameCacheTTL());
        return (ttl.compareTo(MINIMUM_PERIOD) < 0 ? MINIMUM_PERIOD : ttl).toMillis();
    }

    @Override
    public void onFeatureEnd() {
        stopScheduledTask();
    }
}
