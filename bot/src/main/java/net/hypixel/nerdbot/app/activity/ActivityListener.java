package net.hypixel.nerdbot.app.activity;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.core.BotEnvironment;
import net.hypixel.nerdbot.discord.database.model.user.DiscordUser;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.discord.config.EmojiConfig;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.EmojiConfigUtils;
import net.hypixel.nerdbot.discord.config.channel.AlphaProjectConfig;
import net.hypixel.nerdbot.discord.config.objects.RoleRestrictedChannelGroup;
import net.hypixel.nerdbot.discord.cache.suggestion.Suggestion;
import net.hypixel.nerdbot.app.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.discord.database.model.repository.DiscordUserRepository;
import net.hypixel.nerdbot.core.util.ArrayUtils;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ActivityListener {
    private final Map<Long, Long> voiceActivity = new HashMap<>();

    @SubscribeEvent
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        if (event.getUser().isBot()) {
            return;
        }

        log.info("User {} joined {}", event.getUser().getName(), event.getGuild().getName());

        discordUserRepository.findByIdAsync(event.getUser().getId())
            .thenAccept(user -> {
                if (user == null) {
                    DiscordUser newUser = new DiscordUser(event.getUser().getId());
                    discordUserRepository.cacheObject(newUser);
                    log.info("Creating and caching new DiscordUser for user {} ({})", event.getUser().getName(), event.getUser().getId());
                }
            });
    }

    @SubscribeEvent
    public void onGuildMemberLeave(GuildMemberRemoveEvent event) {
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        log.info("User {} left {}", event.getUser().getName(), event.getGuild().getName());

        discordUserRepository.deleteFromDatabaseAsync(event.getUser().getId())
            .thenAccept(result -> {
                if (result.getDeletedCount() == 0) {
                    log.warn("Failed to delete user {} ({}) from the database!", event.getUser().getName(), event.getUser().getId());
                }
            });
    }

    @SubscribeEvent
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        if (event.getChannelType() == net.dv8tion.jda.api.entities.channel.ChannelType.GUILD_PUBLIC_THREAD) {
            Member member = event.getChannel().asThreadChannel().getOwner();
            if (member == null || member.getUser().isBot()) {
                return; // Ignore Empty Member
            }

            DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

            discordUserRepository.findByIdAsync(member.getId())
                .thenAccept(discordUser -> {
                    if (discordUser == null) {
                        return; // Ignore Empty User
                    }

                    String forumChannelId = event.getChannel().asThreadChannel().getParentChannel().getId();
                    long time = System.currentTimeMillis();

                    // New Suggestion
                    if (forumChannelId.equals(DiscordBotEnvironment.getBot().getConfig().getSuggestionConfig().getForumChannelId())) {
                        discordUser.getLastActivity().getSuggestionCreationHistory().add(0, time);
                        log.info("Updating new suggestion activity date for {} to {}", member.getEffectiveName(), time);
                    }

                    // New Alpha Suggestion
                    AlphaProjectConfig alphaProjectConfig = DiscordBotEnvironment.getBot().getConfig().getAlphaProjectConfig();
                    if (ArrayUtils.safeArrayStream(alphaProjectConfig.getAlphaForumIds()).anyMatch(forumChannelId::equalsIgnoreCase)) {
                        discordUser.getLastActivity().getAlphaSuggestionCreationHistory().add(0, time);
                        discordUser.getLastActivity().setLastAlphaActivity(time);
                        log.info("Updating new alpha suggestion activity date for {} to {}", member.getEffectiveName(), time);
                    }

                    // New Project Suggestion
                    if (ArrayUtils.safeArrayStream(alphaProjectConfig.getProjectForumIds()).anyMatch(forumChannelId::equalsIgnoreCase)) {
                        discordUser.getLastActivity().getProjectSuggestionCreationHistory().add(0, time);
                        discordUser.getLastActivity().setLastProjectActivity(time);
                        log.info("Updating new project suggestion activity date for {} to {}", member.getEffectiveName(), time);
                    }
                });
        }
    }

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild()) {
            return; // Ignore Non Guild
        }

        Member member = event.getMember();
        if (member == null || member.getUser().isBot()) {
            return; // Ignore Empty Member
        }

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        discordUserRepository.findByIdAsync(member.getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    return; // Ignore Empty User
                }

                processMessageActivity(event, member, discordUser);
            });
    }

    private void processMessageActivity(MessageReceivedEvent event, Member member, DiscordUser discordUser) {
        GuildMessageChannelUnion guildChannel = event.getGuildChannel();
        // Ignore channel if blacklisted for activity tracking
        if (Arrays.stream(DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getBlacklistedChannels()).anyMatch(guildChannel.getId()::equalsIgnoreCase)) {
            return;
        }

        long time = System.currentTimeMillis();
        Suggestion.ChannelType channelType;

        if (guildChannel instanceof ThreadChannel threadChannel) {
            channelType = DiscordUtils.getThreadSuggestionType(threadChannel);
        } else if (guildChannel instanceof TextChannel) {
            channelType = DiscordUtils.getChannelSuggestionType(guildChannel.asTextChannel());
        } else {
            channelType = DiscordUtils.getChannelSuggestionTypeFromName(guildChannel.getName());
        }

        Optional<RoleRestrictedChannelGroup> matchingGroup = findMatchingRoleRestrictedGroup(guildChannel.getId(), member);
        if (matchingGroup.isPresent()) {
            String groupIdentifier = matchingGroup.get().getIdentifier();

            // Handle thread comments in role-restricted channels
            if (guildChannel instanceof ThreadChannel && event.getChannel().getIdLong() != event.getMessage().getIdLong()) {
                discordUser.getLastActivity().addRoleRestrictedChannelComment(groupIdentifier, time);
                log.info("Updating role-restricted channel group '{}' comment activity for {} to {}",
                    groupIdentifier, member.getEffectiveName(), time);
            }

            // Handle regular messages in role-restricted channels
            GuildChannel targetChannel = guildChannel instanceof ThreadChannel threadChannel
                ? threadChannel.getParentChannel()
                : guildChannel;

            discordUser.getLastActivity().addRoleRestrictedChannelActivity(groupIdentifier, targetChannel.getId(), targetChannel.getName(), 1, time);
            log.info("Updating role-restricted channel group '{}' message activity for {} to {}",
                groupIdentifier, member.getEffectiveName(), time);
        }

        // New Suggestion Comments
        if (guildChannel instanceof ThreadChannel && event.getChannel().getIdLong() != event.getMessage().getIdLong()) {
            ForumChannel forumChannel = guildChannel.asThreadChannel().getParentChannel().asForumChannel();
            channelType = DiscordUtils.getForumSuggestionType(forumChannel);

            // New Suggestion Comments
            if (channelType == Suggestion.ChannelType.NORMAL) {
                discordUser.getLastActivity().getSuggestionCommentHistory().add(0, time);
                log.info("Updating suggestion comment activity date for {} to {}", member.getEffectiveName(), time);
            }

            // New Alpha Suggestion Comments
            if (channelType == Suggestion.ChannelType.ALPHA) {
                discordUser.getLastActivity().getAlphaSuggestionCommentHistory().add(0, time);
                log.info("Updating alpha suggestion comment activity and last alpha activity date for {} to {}", member.getEffectiveName(), time);
            }

            // New Project Suggestion Comments
            if (channelType == Suggestion.ChannelType.PROJECT) {
                discordUser.getLastActivity().getProjectSuggestionCommentHistory().add(0, time);
                log.info("Updating project suggestion comment activity and last project activity date for {} to {}", member.getEffectiveName(), time);
            }
        }

        // Alpha/Project-specific Messages
        if (channelType == Suggestion.ChannelType.ALPHA) {
            discordUser.getLastActivity().setLastAlphaActivity(time);
            log.info("Updating last alpha activity date for " + member.getEffectiveName() + " to " + time);
        } else if (channelType == Suggestion.ChannelType.PROJECT) {
            discordUser.getLastActivity().setLastProjectActivity(time);
            log.info("Updating last project activity date for " + member.getEffectiveName() + " to " + time);
        }

        // Global Messages
        log.info("Updating last global activity date for " + member.getEffectiveName() + " to " + time);
        discordUser.getLastActivity().setLastGlobalActivity(time);

        // Update Channel Message History (only if not already tracked in role-restricted channels)
        if (matchingGroup.isEmpty()) {
            if (!(guildChannel instanceof ThreadChannel threadChannel)) {
                log.debug("Updating channel message history for {} in channel '{}' (ID: {})", member.getEffectiveName(), guildChannel.getName(), guildChannel.getId());
                discordUser.getLastActivity().addChannelHistory(guildChannel.getId(), guildChannel.getName(), time);
            } else {
                GuildChannel parentChannel = threadChannel.getParentChannel();
                log.debug("Updating channel message history for {} in thread '{}' (Parent Channel Name: {}, Parent Channel ID: {}, Thread Channel ID: {})", member.getEffectiveName(), threadChannel.getName(), parentChannel.getName(), parentChannel.getId(), threadChannel.getId());
                discordUser.getLastActivity().addChannelHistory(parentChannel.getId(), parentChannel.getName(), time);
            }
        }
    }

    @SubscribeEvent
    public void onVoiceChannelUpdate(GuildVoiceUpdateEvent event) {
        Member member = event.getMember();

        if (member.getUser().isBot()) {
            return; // Ignore Bots
        }

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        discordUserRepository.findByIdAsync(member.getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    return; // Ignore Empty User
                }

                processVoiceActivity(event, member, discordUser);
            });
    }

    private void processVoiceActivity(GuildVoiceUpdateEvent event, Member member, DiscordUser discordUser) {
        long time = System.currentTimeMillis();

        if (this.voiceActivity.containsKey(member.getIdLong())) {
            AudioChannelUnion channelLeft = event.getChannelLeft();

            if (channelLeft != null) {
                long timeSpent = time - this.voiceActivity.get(member.getIdLong());

                PrometheusMetrics.TOTAL_VOICE_TIME_SPENT_BY_USER.labels(member.getEffectiveName(), channelLeft.getName()).inc((TimeUnit.MILLISECONDS.toSeconds(timeSpent)));

                if ((timeSpent / 1_000L) > SkyBlockNerdsBot.config().getVoiceThreshold()) {
                    Suggestion.ChannelType channelType = DiscordUtils.getChannelSuggestionType(channelLeft.asVoiceChannel());

                    if (channelType == Suggestion.ChannelType.ALPHA) {
                        discordUser.getLastActivity().setAlphaVoiceJoinDate(time);
                        log.info("Updating last alpha voice activity for {} to {}", member.getEffectiveName(), time);
                    } else if (channelType == Suggestion.ChannelType.PROJECT) {
                        discordUser.getLastActivity().setProjectVoiceJoinDate(time);
                        log.info("Updating last project voice activity for {} to {}", member.getEffectiveName(), time);
                    } else {
                        discordUser.getLastActivity().setLastVoiceChannelJoinDate(time);
                        log.info("Updating last global voice activity for {} to {}", member.getEffectiveName(), time);
                    }

                    // Check if voice channel belongs to a role-restricted group
                    Optional<RoleRestrictedChannelGroup> matchingGroup = findMatchingRoleRestrictedGroup(channelLeft.getId(), member);
                    if (matchingGroup.isPresent()) {
                        String groupIdentifier = matchingGroup.get().getIdentifier();
                        discordUser.getLastActivity().getRoleRestrictedChannelLastActivity().put(groupIdentifier, time);
                        log.info("Updating role-restricted channel group '{}' voice activity for {} to {}",
                            groupIdentifier, member.getEffectiveName(), time);
                    }
                }
            }

            this.voiceActivity.remove(member.getIdLong());
        }

        if (event.getChannelJoined() != null) {
            this.voiceActivity.put(member.getIdLong(), time);
            PrometheusMetrics.TOTAL_VOICE_CONNECTIONS_BY_USER.labels(member.getEffectiveName(), event.getChannelJoined().getName()).inc();
        }
    }

    @SubscribeEvent
    public void onActivityReactionAdd(MessageReactionAddEvent event) {
        if (!event.isFromGuild() || event.getReaction().getEmoji().getType() != Emoji.Type.CUSTOM) {
            return; // Ignore non-guild and native emojis
        }

        Member member = event.getMember();
        if (member == null || member.getUser().isBot()) {
            return; // Ignore Empty Member
        }

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        discordUserRepository.findByIdAsync(member.getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    return; // Ignore Empty User
                }

                processReactionActivity(event, member, discordUser);
            });
    }

    private void processReactionActivity(MessageReactionAddEvent event, Member member, DiscordUser discordUser) {
        if (event.getChannelType() != net.dv8tion.jda.api.entities.channel.ChannelType.GUILD_PUBLIC_THREAD) {
            return; // Not A Thread
        }

        if (!event.getMessageId().equals(event.getChannel().getId())) {
            return; // Not Original Message
        }

        EmojiConfig emojiConfig = DiscordBotEnvironment.getBot().getConfig().getEmojiConfig();

        if (EmojiConfigUtils.isReactionEquals(emojiConfig, event.getReaction(), EmojiConfig::getAgreeEmojiId)
            || EmojiConfigUtils.isReactionEquals(emojiConfig, event.getReaction(), EmojiConfig::getDisagreeEmojiId)
            || EmojiConfigUtils.isReactionEquals(emojiConfig, event.getReaction(), EmojiConfig::getNeutralEmojiId)) {

            ThreadChannel threadChannel = event.getChannel().asThreadChannel();
            String forumChannelId = threadChannel.getParentChannel().getId();
            long time = System.currentTimeMillis();

            // Check role-restricted channel groups for voting
            Optional<RoleRestrictedChannelGroup> matchingGroup = findMatchingRoleRestrictedGroup(forumChannelId, member);
            if (matchingGroup.isPresent()) {
                String groupIdentifier = matchingGroup.get().getIdentifier();
                discordUser.getLastActivity().addRoleRestrictedChannelVote(groupIdentifier, threadChannel.getId(), time);
                log.info("Updating role-restricted channel group '{}' voting activity for {} to {}",
                    groupIdentifier, member.getEffectiveName(), time);
            }

            // New Suggestion Voting
            if (forumChannelId.equals(DiscordBotEnvironment.getBot().getConfig().getSuggestionConfig().getForumChannelId())) {
                discordUser.getLastActivity().getSuggestionVoteHistoryMap().putIfAbsent(threadChannel.getId(), time);
                DiscordBotEnvironment.getBot().getSuggestionCache().updateSuggestion(threadChannel);
                log.info("Updating suggestion voting activity date for " + member.getEffectiveName() + " to " + time);
            }

            // New Alpha Suggestion Voting
            AlphaProjectConfig alphaProjectConfig = DiscordBotEnvironment.getBot().getConfig().getAlphaProjectConfig();
            if (ArrayUtils.safeArrayStream(alphaProjectConfig.getAlphaForumIds()).anyMatch(forumChannelId::equalsIgnoreCase)) {
                discordUser.getLastActivity().getAlphaSuggestionVoteHistoryMap().putIfAbsent(threadChannel.getId(), time);
                DiscordBotEnvironment.getBot().getSuggestionCache().updateSuggestion(threadChannel);
                log.info("Updating alpha suggestion voting activity date for " + member.getEffectiveName() + " to " + time);
            }

            // New Project Suggestion Voting
            if (ArrayUtils.safeArrayStream(alphaProjectConfig.getProjectForumIds()).anyMatch(forumChannelId::equalsIgnoreCase)) {
                discordUser.getLastActivity().getProjectSuggestionVoteHistoryMap().putIfAbsent(threadChannel.getId(), time);
                DiscordBotEnvironment.getBot().getSuggestionCache().updateSuggestion(threadChannel);
                log.info("Updating project suggestion voting activity date for " + member.getEffectiveName() + " to " + time);
            }
        }
    }

    /**
     * Find a matching role-restricted channel group for the given channel and member
     */
    private Optional<RoleRestrictedChannelGroup> findMatchingRoleRestrictedGroup(String channelId, Member member) {
        List<RoleRestrictedChannelGroup> groups = DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getRoleRestrictedChannelGroups();

        for (RoleRestrictedChannelGroup group : groups) {
            boolean channelMatches = Arrays.stream(group.getChannelIds())
                .anyMatch(groupChannelId -> groupChannelId.equalsIgnoreCase(channelId));

            if (!channelMatches) {
                continue;
            }

            boolean hasRequiredRole = Arrays.stream(group.getRequiredRoleIds())
                .anyMatch(roleId -> member.getRoles().stream()
                    .map(Role::getId)
                    .anyMatch(memberRoleId -> memberRoleId.equalsIgnoreCase(roleId)));

            if (hasRequiredRole) {
                return Optional.of(group);
            }
        }

        return Optional.empty();
    }
}