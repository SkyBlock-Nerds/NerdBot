package net.hypixel.nerdbot.curator;

import net.dv8tion.jda.api.entities.*;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.channel.Channel;
import net.hypixel.nerdbot.channel.Reactions;
import net.hypixel.nerdbot.config.BotConfig;
import net.hypixel.nerdbot.database.Database;
import net.hypixel.nerdbot.database.GreenlitMessage;
import net.hypixel.nerdbot.util.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Curator {

    private final int limit;

    private final TextChannel channel;

    private final List<GreenlitMessage> greenlitMessages;

    public Curator(int limit, Channel channel) {
        this.limit = limit;
        this.channel = NerdBotApp.getBot().getJDA().getTextChannelById(channel.getId());
        greenlitMessages = new ArrayList<>(limit);
    }

    public Curator(Channel channel) {
        this(100, channel);
    }

    public void curate() {
        MessageHistory history = channel.getHistory();
        List<Message> messages = history.retrievePast(limit).complete();

        for (Message message : messages) {
            if (message.getAuthor().isBot() || message.getReactionById(Reactions.GREENLIT.getId()) != null) {
                continue;
            }

            int positive = 0, negative = 0;

            for (MessageReaction reaction : message.getReactions()) {
                if (reaction.getReactionEmote().isEmoji()) {
                    continue;
                }

                if (reaction.getReactionEmote().getId().equals(Reactions.AGREE.getId())) {
                    positive = reaction.getCount();
                }

                if (reaction.getReactionEmote().getId().equals(Reactions.DISAGREE.getId())) {
                    negative = reaction.getCount();
                }
            }

            BotConfig config = NerdBotApp.getBot().getConfig();

            if (positive == 0 || negative == 0 || positive < config.getMinimumThreshold()) {
                continue;
            }

            double ratio = getRatio(positive, negative);

            if (ratio < config.getPercentage()) {
                continue;
            }

            GreenlitMessage msg = new GreenlitMessage(message.getAuthor().getId(), message.getId(), message.getContentRaw(), new Date(message.getTimeCreated().toInstant().toEpochMilli()), message.getJumpUrl());
            greenlitMessages.add(msg);
        }
    }

    public void applyEmoji() {
        Guild guild = channel.getGuild();
        Emote greenlitEmoji = guild.getEmoteById(Reactions.GREENLIT.getId());
        List<Message> messages = channel.getHistory().retrievePast(limit).complete();

        TextChannel logChannel = NerdBotApp.getBot().getJDA().getTextChannelById(Channel.SUGGESTIONS.getId());


        for (GreenlitMessage msg : greenlitMessages) {
            logChannel.retrieveMessageById(msg.getMessageId()).queue(message -> {
                message.addReaction(greenlitEmoji).queue();
            });
        }

        Logger.info("Applied greenlit emoji to " + greenlitMessages.size() + " messages");
    }

    public void insert() {
        if (!greenlitMessages.isEmpty()) {
            Database.getInstance().insertGreenlitMessages(greenlitMessages);
        } else {
            Logger.info("No greenlit messages to insert!");
        }
    }

    private double getRatio(int positive, int negative) {
        return (double) positive / (positive + negative) * 100;
    }

    public int getLimit() {
        return limit;
    }

    public TextChannel getChannel() {
        return channel;
    }

    public List<GreenlitMessage> getGreenlitMessages() {
        return greenlitMessages;
    }

}
