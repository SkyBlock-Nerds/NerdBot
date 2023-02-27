package net.hypixel.nerdbot.listener;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.user.DiscordUser;
import net.hypixel.nerdbot.bot.config.BotConfig;
import net.hypixel.nerdbot.bot.config.EmojiConfig;
import net.hypixel.nerdbot.util.Util;

import java.util.Arrays;

@Log4j2
public class ActivityListener {

    private final Database database = NerdBotApp.getBot().getDatabase();

    @SubscribeEvent
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Util.getOrAddUserToCache(this.database, event.getUser().getId());
        log.info("User " + event.getUser().getAsTag() + " joined guild " + event.getGuild().getName());
    }

    @SubscribeEvent
    public void onGuildMemberLeave(GuildMemberRemoveEvent event) {
        this.database.deleteDocument(this.database.getCollection("users"), "discordId", event.getUser().getId());
        log.info("User " + event.getUser().getAsTag() + " left guild " + event.getGuild().getName());
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

        DiscordUser discordUser = Util.getOrAddUserToCache(this.database, member.getId());
        if (discordUser == null) {
            return; // Ignore Empty User
        }

        GuildMessageChannelUnion guildChannel = event.getGuildChannel();
        String channelId = guildChannel.getId();
        long time = System.currentTimeMillis();
        boolean isAlphaChannel = guildChannel.getName().contains("alpha");

        // New Suggestions
        if (Arrays.stream(NerdBotApp.getBot().getConfig().getSuggestionForumIds()).anyMatch(channelId::equalsIgnoreCase)) {
            discordUser.getLastActivity().setLastSuggestionDate(time);
            log.info("Updating new suggestion activity date for " + member.getEffectiveName() + " to " + time);
        }

        // New Alpha Suggestions
        if (Arrays.stream(NerdBotApp.getBot().getConfig().getAlphaSuggestionForumIds()).anyMatch(channelId::equalsIgnoreCase)) {
            isAlphaChannel = true;
            discordUser.getLastActivity().setLastAlphaSuggestionDate(time);
            log.info("Updating new alpha suggestion activity date for " + member.getEffectiveName() + " to " + time);
        }

        // New Suggestion Comments
        if (guildChannel instanceof ThreadChannel) {
            String forumChannelId = guildChannel.asThreadChannel().getParentChannel().getId();

            // New Suggestion Comments
            if (Arrays.stream(NerdBotApp.getBot().getConfig().getSuggestionForumIds()).anyMatch(forumChannelId::equalsIgnoreCase)) {
                discordUser.getLastActivity().setSuggestionCommentDate(time);
                log.info("Updating suggestion comment activity date for " + member.getEffectiveName() + " to " + time);
            }

            // New Alpha Suggestion Comments
            if (Arrays.stream(NerdBotApp.getBot().getConfig().getAlphaSuggestionForumIds()).anyMatch(forumChannelId::equalsIgnoreCase)) {
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

        discordUser.getLastActivity().getChannelActivity().put(guildChannel.getId(), discordUser.getLastActivity().getChannelActivity().getOrDefault(guildChannel.getId(), 0) + 1);
    }

    @SubscribeEvent
    public void onVoiceChannelJoin(GuildVoiceUpdateEvent event) {
        Member member = event.getMember();

        if (member.getUser().isBot()) {
            return; // Ignore Bots
        }

        DiscordUser discordUser = Util.getOrAddUserToCache(this.database, member.getId());
        if (discordUser == null) {
            return; // Ignore Empty User
        }

        if (event.getChannelJoined() == null) {
            return; // Ignore Leave Events
        }

        long time = System.currentTimeMillis();
        if (event.getChannelJoined().getName().toLowerCase().contains("alpha")) {
            discordUser.getLastActivity().setAlphaVoiceJoinDate(time);
            log.info("Updating last alpha voice activity date for " + member.getEffectiveName() + " to " + time);
        } else {
            discordUser.getLastActivity().setLastVoiceChannelJoinDate(time);
            log.info("Updating last global voice activity date for " + member.getEffectiveName() + " to " + time);
        }
    }

    @SubscribeEvent
    public void onReactionReceived(MessageReactionAddEvent event) {
        if (!event.isFromGuild()) {
            return; // Ignore Non Guild
        }

        Member member = event.getMember();
        if (member == null || member.getUser().isBot()) {
            return; // Ignore Empty Member
        }

        DiscordUser discordUser = Util.getOrAddUserToCache(this.database, member.getId());

        if (discordUser == null) {
            return; // Ignore Empty User
        }

        if (event.getReaction().getEmoji().getType() != Emoji.Type.CUSTOM) {
            return; // Ignore Native Emojis
        }

        if (event.getGuildChannel() instanceof ThreadChannel) {
            BotConfig config = NerdBotApp.getBot().getConfig();
            EmojiConfig emojiConfig = config.getEmojiConfig();

            if (emojiConfig.isEquals(event.getReaction(), EmojiConfig::getAgreeEmojiId) || emojiConfig.isEquals(event.getReaction(), EmojiConfig::getDisagreeEmojiId)) {
                ThreadChannel threadChannel = event.getGuildChannel().asThreadChannel();
                MessageHistory history = threadChannel.getHistoryFromBeginning(1).complete();
                Message message = history.getRetrievedHistory().get(0);

                if (message == null) {
                    log.error("Message for thread '" + threadChannel.getName() + "' (ID: " + threadChannel.getId() + ") is null!");
                    return;
                }

                String forumChannelId = threadChannel.getParentChannel().getId();
                long time = System.currentTimeMillis();

                // New Suggestion Voting
                if (Arrays.stream(NerdBotApp.getBot().getConfig().getSuggestionForumIds()).anyMatch(forumChannelId::equalsIgnoreCase)) {
                    discordUser.getLastActivity().setSuggestionVoteDate(time);
                    log.info("Updating suggestion voting activity date for " + member.getEffectiveName() + " to " + time);
                }

                // New Alpha Suggestion Voting
                if (Arrays.stream(NerdBotApp.getBot().getConfig().getAlphaSuggestionForumIds()).anyMatch(forumChannelId::equalsIgnoreCase)) {
                    discordUser.getLastActivity().setAlphaSuggestionVoteDate(time);
                    log.info("Updating alpha suggestion voting activity date for " + member.getEffectiveName() + " to " + time);
                }
            }
        }

    }
}
