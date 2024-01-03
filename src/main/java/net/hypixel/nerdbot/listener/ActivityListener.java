package net.hypixel.nerdbot.listener;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
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
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.bot.config.SuggestionConfig;
import net.hypixel.nerdbot.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.exception.RepositoryException;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Log4j2
public class ActivityListener {
    private final Map<Long, Long> voiceActivity = new HashMap<>();

    @SubscribeEvent
    public void onGuildMemberJoin(GuildMemberJoinEvent event) throws RepositoryException {
        UpdateResult result = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class).saveToDatabase(new DiscordUser(event.getMember()));

        if (!result.wasAcknowledged() || result.getModifiedCount() == 0) {
            throw new RepositoryException("Failed to save new user '" + event.getUser().getName() + "' to database!");
        }

        log.info("User " + event.getUser().getName() + " joined guild " + event.getGuild().getName());
    }

    @SubscribeEvent
    public void onGuildMemberLeave(GuildMemberRemoveEvent event) throws RepositoryException {
        DeleteResult result = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class).deleteFromDatabase(event.getUser().getId());

        if (!result.wasAcknowledged() || result.getDeletedCount() == 0) {
            throw new RepositoryException("Failed to delete user '" + event.getUser().getName() + "' from database!");
        }

        log.info("User " + event.getUser().getName() + " left guild " + event.getGuild().getName());
    }

    @SubscribeEvent
    public void onThreadCreateEvent(@NotNull ChannelCreateEvent event) {
        if (event.getChannelType() == ChannelType.GUILD_PUBLIC_THREAD) {
            Member member = event.getChannel().asThreadChannel().getOwner();
            if (member == null || member.getUser().isBot()) {
                return; // Ignore Empty Member
            }

            DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
            DiscordUser discordUser = discordUserRepository.findById(member.getId());
            if (discordUser == null) {
                return; // Ignore Empty User
            }

            String forumChannelId = event.getChannel().asThreadChannel().getParentChannel().getId();
            long time = System.currentTimeMillis();
            SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();

            // New Suggestions
            if (Util.safeArrayStream(suggestionConfig.getSuggestionForumIds()).anyMatch(forumChannelId::equalsIgnoreCase)) {
                discordUser.getLastActivity().setLastSuggestionDate(time);
                log.info("Updating new suggestion activity date for " + member.getEffectiveName() + " to " + time);
            }

            // New Alpha Suggestions
            if (Util.safeArrayStream(suggestionConfig.getAlphaSuggestionForumIds()).anyMatch(forumChannelId::equalsIgnoreCase)) {
                discordUser.getLastActivity().setLastAlphaSuggestionDate(time);
                log.info("Updating new alpha suggestion activity date for " + member.getEffectiveName() + " to " + time);
            }
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

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(member.getId());
        if (discordUser == null) {
            return; // Ignore Empty User
        }

        GuildMessageChannelUnion guildChannel = event.getGuildChannel();
        long time = System.currentTimeMillis();
        boolean isAlphaChannel = guildChannel.getName().contains("alpha");
        SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();

        // New Suggestion Comments
        if (guildChannel instanceof ThreadChannel && event.getChannel().getIdLong() != event.getMessage().getIdLong()) {
            String forumChannelId = guildChannel.asThreadChannel().getParentChannel().getId();

            // New Suggestion Comments
            if (Util.safeArrayStream(suggestionConfig.getSuggestionForumIds()).anyMatch(forumChannelId::equalsIgnoreCase)) {
                discordUser.getLastActivity().setSuggestionCommentDate(time);
                log.info("Updating suggestion comment activity date for " + member.getEffectiveName() + " to " + time);
            }

            // New Alpha Suggestion Comments
            if (Util.safeArrayStream(suggestionConfig.getAlphaSuggestionForumIds()).anyMatch(forumChannelId::equalsIgnoreCase)) {
                isAlphaChannel = true;
                discordUser.getLastActivity().setAlphaSuggestionCommentDate(time);
                log.info("Updating alpha suggestion comment activity date for " + member.getEffectiveName() + " to " + time);
            }
        }

        if (isAlphaChannel) {
            discordUser.getLastActivity().setLastAlphaActivity(time);
            log.info("Updating last alpha activity date for " + member.getEffectiveName() + " to " + time);
        }

        discordUser.getLastActivity().setLastGlobalActivity(time);
        log.info("Updating last global activity date for " + member.getEffectiveName() + " to " + time);

        // Ignore channel if blacklisted for activity tracking
        if (Arrays.stream(NerdBotApp.getBot().getConfig().getChannelConfig().getBlacklistedChannels()).anyMatch(guildChannel.getId()::equalsIgnoreCase)) {
            return;
        }

        discordUser.getLastActivity().getChannelActivity().put(guildChannel.getId(), discordUser.getLastActivity().getChannelActivity().getOrDefault(guildChannel.getId(), 0) + 1);
    }

    @SubscribeEvent
    public void onVoiceChannelJoin(GuildVoiceUpdateEvent event) {
        Member member = event.getMember();

        if (member.getUser().isBot()) {
            return; // Ignore Bots
        }

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(member.getId());
        if (discordUser == null) {
            return; // Ignore Empty User
        }

        long time = System.currentTimeMillis();

        if (this.voiceActivity.containsKey(member.getIdLong())) {
            AudioChannelUnion channelLeft = event.getChannelLeft();

            if (channelLeft != null) {
                long timeSpent = time - this.voiceActivity.get(member.getIdLong());

                PrometheusMetrics.TOTAL_VOICE_TIME_SPENT_BY_USER.labels(member.getEffectiveName(), channelLeft.getName()).inc((TimeUnit.MILLISECONDS.toSeconds(timeSpent)));

                if ((timeSpent / 1_000L) > NerdBotApp.getBot().getConfig().getVoiceThreshold()) {
                    if (channelLeft.getName().toLowerCase().contains("alpha")) {
                        discordUser.getLastActivity().setAlphaVoiceJoinDate(time);
                        log.info("Updating last alpha voice activity for " + member.getEffectiveName() + " to " + time);
                    } else {
                        discordUser.getLastActivity().setLastVoiceChannelJoinDate(time);
                        log.info("Updating last global voice activity for " + member.getEffectiveName() + " to " + time);
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
    public void onReactionReceived(MessageReactionAddEvent event) {
        if (!event.isFromGuild() || event.getReaction().getEmoji().getType() != Emoji.Type.CUSTOM) {
            return; // Ignore non-guild and native emojis
        }

        Member member = event.getMember();
        if (member == null || member.getUser().isBot()) {
            return; // Ignore Empty Member
        }

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(member.getId());
      
        if (discordUser == null) {
            return; // Ignore Empty User
        }

        if (event.getGuildChannel() instanceof ThreadChannel threadChannel) {
            SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();

            if (suggestionConfig.isReactionEquals(event.getReaction(), SuggestionConfig::getAgreeEmojiId) ||
                suggestionConfig.isReactionEquals(event.getReaction(), SuggestionConfig::getDisagreeEmojiId) ||
                suggestionConfig.isReactionEquals(event.getReaction(), SuggestionConfig::getNeutralEmojiId))
            {
                MessageHistory history = threadChannel.getHistoryFromBeginning(1).complete();
                boolean deleted = history.isEmpty() || history.getRetrievedHistory().get(0).getIdLong() != threadChannel.getIdLong();

                if (deleted) {
                    log.error("Original message for thread '" + threadChannel.getName() + "' (ID: " + threadChannel.getId() + ") is gone!");
                    return;
                }

                String forumChannelId = threadChannel.getParentChannel().getId();
                long time = System.currentTimeMillis();

                // New Suggestion Voting
                if (Util.safeArrayStream(suggestionConfig.getSuggestionForumIds()).anyMatch(forumChannelId::equalsIgnoreCase)) {
                    discordUser.getLastActivity().setSuggestionVoteDate(time);
                    NerdBotApp.getSuggestionCache().updateSuggestion(threadChannel);
                    log.info("Updating suggestion voting activity date for " + member.getEffectiveName() + " to " + time);
                }

                // New Alpha Suggestion Voting
                if (Util.safeArrayStream(suggestionConfig.getAlphaSuggestionForumIds()).anyMatch(forumChannelId::equalsIgnoreCase)) {
                    discordUser.getLastActivity().setAlphaSuggestionVoteDate(time);
                    NerdBotApp.getSuggestionCache().updateSuggestion(threadChannel);
                    log.info("Updating alpha suggestion voting activity date for " + member.getEffectiveName() + " to " + time);
                }
            }
        }
    }
}
