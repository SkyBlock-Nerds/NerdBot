package net.hypixel.nerdbot.app.activity;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.listener.GuildScope;
import net.hypixel.nerdbot.app.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.app.suggestion.Suggestion;
import net.hypixel.nerdbot.app.suggestion.SuggestionTypeResolver;
import net.hypixel.nerdbot.app.user.MemberQueue;
import net.hypixel.nerdbot.discord.config.objects.RoleRestrictedChannelGroup;
import net.hypixel.nerdbot.discord.util.EmojiConfigUtils;
import net.hypixel.nerdbot.marmalade.collections.ArrayUtils;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.LastActivity;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Records the messages, suggestions, votes and voice time the nomination and inactivity sweeps
 * judge members on. Each change goes through the member's queue, so it can't race their other
 * updates.
 */
@Slf4j
public class ActivityListener {

    private static final CompletableFuture<Void> NOTHING_RECORDED = CompletableFuture.completedFuture(null);

    private final GuildScope guildScope;
    private final MemberQueue queue;
    private final Provider<NerdBotConfig> config;
    private final Clock clock;
    // When each member joined the voice channel they're in, in epoch millis
    private final Map<String, Long> voiceSessions = new ConcurrentHashMap<>();

    @Inject
    public ActivityListener(GuildScope guildScope, MemberQueue queue, Provider<NerdBotConfig> config, Clock clock, JDA jda) {
        this.guildScope = guildScope;
        this.queue = queue;
        this.config = config;
        this.clock = clock;
        startSessionsForConnectedMembers(jda);
    }

    // Listeners register once the bot is ready, so members already in voice never send a join event
    private void startSessionsForConnectedMembers(JDA jda) {
        long now = clock.millis();
        jda.getGuilds().stream()
            .filter(guildScope::isMainGuild)
            .flatMap(guild -> guild.getVoiceStates().stream())
            .filter(GuildVoiceState::inAudioChannel)
            .map(GuildVoiceState::getMember)
            .filter(member -> !member.getUser().isBot())
            .forEach(member -> voiceSessions.put(member.getId(), now));
    }

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) {
        Member member = event.getMember();
        if (!event.isFromGuild() || !guildScope.isMainGuild(event.getGuild()) || event.isWebhookMessage()
            || member == null || member.getUser().isBot()) {
            return;
        }

        boolean threadStarter = event.getMessageIdLong() == event.getChannel().getIdLong();
        recordMessage(member, event.getGuildChannel(), threadStarter);
    }

    /**
     * @param threadStarter whether this is the message a thread was started from, which isn't a
     *                      comment on it
     */
    CompletableFuture<Void> recordMessage(Member member, GuildMessageChannel channel, boolean threadStarter) {
        NerdBotConfig current = config.get();
        GuildChannel home = channel instanceof ThreadChannel thread ? thread.getParentChannel() : channel;
        String[] blacklist = current.getChannelConfig().getBlacklistedChannels();
        if (ArrayUtils.safeArrayStream(blacklist).anyMatch(id -> id.equals(channel.getId()) || id.equals(home.getId()))) {
            return NOTHING_RECORDED;
        }

        Suggestion.ChannelType type = channel instanceof ThreadChannel thread
            ? SuggestionTypeResolver.getThreadSuggestionType(thread, current)
            : SuggestionTypeResolver.getChannelSuggestionTypeFromName(channel.getName(), current);
        boolean comment = channel instanceof ThreadChannel && !threadStarter;
        boolean forumComment = comment && home.getType() == ChannelType.FORUM;
        Optional<String> group = restrictedGroup(home.getId(), member, current);
        String homeId = home.getId();
        String homeName = home.getName();
        long time = clock.millis();

        return queue.withRecord(member.getId(), "message activity", user -> {
            LastActivity activity = user.getLastActivity();

            if (forumComment) {
                switch (type) {
                    case NORMAL -> activity.getSuggestionCommentHistory().add(0, time);
                    case ALPHA -> activity.getAlphaSuggestionCommentHistory().add(0, time);
                    case PROJECT -> activity.getProjectSuggestionCommentHistory().add(0, time);
                    default -> {
                    }
                }
            }

            if (type == Suggestion.ChannelType.ALPHA) {
                activity.setLastAlphaActivity(time);
            } else if (type == Suggestion.ChannelType.PROJECT) {
                activity.setLastProjectActivity(time);
            }

            activity.setLastGlobalActivity(time);

            group.ifPresentOrElse(identifier -> {
                if (comment) {
                    activity.addRoleRestrictedChannelComment(identifier, time);
                }
                activity.addRoleRestrictedChannelActivity(identifier, homeId, homeName, 1, time);
            }, () -> activity.addChannelHistory(homeId, homeName, time));

            log.debug("Recorded a {} message from {} in {} ({})", type, member.getId(), homeName, homeId);
        });
    }

    @SubscribeEvent
    public void onChannelCreate(ChannelCreateEvent event) {
        if (event.getChannelType() != ChannelType.GUILD_PUBLIC_THREAD || !guildScope.isMainGuild(event.getGuild())) {
            return;
        }

        ThreadChannel thread = event.getChannel().asThreadChannel();
        Member owner = thread.getOwner();
        boolean ownedByBot = owner != null ? owner.getUser().isBot() : thread.getOwnerIdLong() == thread.getJDA().getSelfUser().getIdLong();
        if (!ownedByBot) {
            recordSuggestionCreated(thread.getOwnerId(), thread);
        }
    }

    CompletableFuture<Void> recordSuggestionCreated(String ownerId, ThreadChannel thread) {
        Optional<Suggestion.ChannelType> type = SuggestionTypeResolver.getConfiguredForumType(thread.getParentChannel().getId(), config.get());
        if (type.isEmpty()) {
            return NOTHING_RECORDED;
        }

        long time = clock.millis();
        return queue.withRecord(ownerId, "new suggestion", user -> {
            LastActivity activity = user.getLastActivity();
            switch (type.get()) {
                case NORMAL -> activity.getSuggestionCreationHistory().add(0, time);
                case ALPHA -> {
                    activity.getAlphaSuggestionCreationHistory().add(0, time);
                    activity.setLastAlphaActivity(time);
                }
                case PROJECT -> {
                    activity.getProjectSuggestionCreationHistory().add(0, time);
                    activity.setLastProjectActivity(time);
                }
                default -> {
                }
            }
            log.debug("Recorded a {} suggestion from {}", type.get(), ownerId);
        });
    }

    @SubscribeEvent
    public void onReactionAdd(MessageReactionAddEvent event) {
        Member member = event.getMember();
        if (!event.isFromGuild() || !guildScope.isMainGuild(event.getGuild()) || member == null || member.getUser().isBot()) {
            return;
        }

        boolean onSuggestionPost = event.getChannelType() == ChannelType.GUILD_PUBLIC_THREAD
            && event.getMessageId().equals(event.getChannel().getId());
        if (onSuggestionPost && EmojiConfigUtils.isVote(config.get().getEmojiConfig(), event.getReaction())) {
            recordVote(member, event.getChannel().asThreadChannel());
        }
    }

    CompletableFuture<Void> recordVote(Member member, ThreadChannel thread) {
        NerdBotConfig current = config.get();
        String forumId = thread.getParentChannel().getId();
        Optional<String> group = restrictedGroup(forumId, member, current);
        Optional<Suggestion.ChannelType> type = SuggestionTypeResolver.getConfiguredForumType(forumId, current);
        if (group.isEmpty() && type.isEmpty()) {
            return NOTHING_RECORDED;
        }

        String threadId = thread.getId();
        long time = clock.millis();
        return queue.withRecord(member.getId(), "vote", user -> {
            LastActivity activity = user.getLastActivity();
            group.ifPresent(identifier -> activity.addRoleRestrictedChannelVote(identifier, threadId, time));
            type.ifPresent(kind -> {
                switch (kind) {
                    case NORMAL -> activity.getSuggestionVoteHistoryMap().putIfAbsent(threadId, time);
                    case ALPHA -> activity.getAlphaSuggestionVoteHistoryMap().putIfAbsent(threadId, time);
                    case PROJECT -> activity.getProjectSuggestionVoteHistoryMap().putIfAbsent(threadId, time);
                    default -> {
                    }
                }
            });
            log.debug("Recorded a vote from {} on {}", member.getId(), threadId);
        });
    }

    @SubscribeEvent
    public void onVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (guildScope.isMainGuild(event.getGuild()) && !event.getMember().getUser().isBot()) {
            recordVoiceUpdate(event.getMember(), event.getChannelLeft(), event.getChannelJoined());
        }
    }

    /** Moving between channels is a leave and a join. */
    CompletableFuture<Void> recordVoiceUpdate(Member member, @Nullable AudioChannel left, @Nullable AudioChannel joined) {
        long time = clock.millis();
        CompletableFuture<Void> recorded = NOTHING_RECORDED;

        if (left != null) {
            Long joinedAt = voiceSessions.remove(member.getId());
            if (joinedAt != null) {
                recorded = recordVoiceSession(member, left, time - joinedAt, time);
            }
        }

        if (joined != null) {
            voiceSessions.put(member.getId(), time);
            PrometheusMetrics.VOICE_CONNECTIONS.labels(joined.getName()).inc();
        }

        return recorded;
    }

    private CompletableFuture<Void> recordVoiceSession(Member member, AudioChannel channel, long spentMs, long time) {
        PrometheusMetrics.VOICE_TIME_SECONDS.labels(channel.getName()).inc(TimeUnit.MILLISECONDS.toSeconds(spentMs));

        NerdBotConfig current = config.get();
        if (TimeUnit.MILLISECONDS.toSeconds(spentMs) <= current.getVoiceThreshold()) {
            return NOTHING_RECORDED;
        }

        Suggestion.ChannelType type = SuggestionTypeResolver.getChannelSuggestionTypeFromName(channel.getName(), current);
        Optional<String> group = restrictedGroup(channel.getId(), member, current);
        return queue.withRecord(member.getId(), "voice activity", user -> {
            LastActivity activity = user.getLastActivity();
            switch (type) {
                case ALPHA -> activity.setAlphaVoiceJoinDate(time);
                case PROJECT -> activity.setProjectVoiceJoinDate(time);
                default -> activity.setLastVoiceChannelJoinDate(time);
            }
            group.ifPresent(identifier -> activity.getRoleRestrictedChannelLastActivity().put(identifier, time));
            log.debug("Recorded {}s of {} voice time for {}", TimeUnit.MILLISECONDS.toSeconds(spentMs), type, member.getId());
        });
    }

    /** The restricted channel group {@code channelId} belongs to, if the member has one of its roles. */
    private static Optional<String> restrictedGroup(String channelId, Member member, NerdBotConfig config) {
        Set<String> roleIds = member.getRoles().stream().map(Role::getId).collect(Collectors.toSet());
        return config.getChannelConfig().getRoleRestrictedChannelGroups().stream()
            .filter(group -> ArrayUtils.safeArrayStream(group.getChannelIds()).anyMatch(channelId::equals))
            .filter(group -> ArrayUtils.safeArrayStream(group.getRequiredRoleIds()).anyMatch(roleIds::contains))
            .map(RoleRestrictedChannelGroup::getIdentifier)
            .findFirst();
    }
}
