package net.hypixel.nerdbot.curator;

import net.dv8tion.jda.api.entities.*;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.channel.ChannelGroup;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.channel.Reactions;
import net.hypixel.nerdbot.config.BotConfig;
import net.hypixel.nerdbot.database.Database;
import net.hypixel.nerdbot.database.GreenlitMessage;
import net.hypixel.nerdbot.util.Logger;
import net.hypixel.nerdbot.util.Util;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Curator {

    private final int limit;
    private final ChannelGroup group;
    private final List<GreenlitMessage> greenlitMessages;

    public Curator(int limit, ChannelGroup group) {
        this.limit = limit;
        this.group = group;
        greenlitMessages = new ArrayList<>(limit);
    }

    public Curator(ChannelGroup group) {
        this(100, group);
    }

    public void curate() {
        TextChannel textChannel = ChannelManager.getChannel(group.getFrom());
        if (textChannel == null) return;

        MessageHistory history = textChannel.getHistory();
        List<Message> messages = history.retrievePast(limit).complete();

        Logger.info("Starting suggestion curation at " + new Date());

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
                    positive = reaction.getCount() - 1;
                }

                if (reaction.getReactionEmote().getId().equals(Reactions.DISAGREE.getId())) {
                    negative = reaction.getCount() - 1;
                }
            }

            BotConfig config = NerdBotApp.getBot().getConfig();

            if (positive == 0 && negative == 0 || positive < config.getMinimumThreshold()) {
                Logger.info("Message " + message.getId() + " is below the minimum threshold! (" + positive + "/" + negative + ") (min threshold: " + config.getMinimumThreshold() + ")");
                continue;
            }

            double ratio = getRatio(positive, negative);

            Logger.info("Message " + message.getId() + " has a ratio of " + getRatio(positive, negative) + "%");

            if (ratio < config.getPercentage()) {
                continue;
            }

            GreenlitMessage msg = new GreenlitMessage()
                    .setUserId(message.getAuthor().getId())
                    .setMessageId(message.getId())
                    .setSuggestionContent(message.getContentRaw())
                    .setSuggestionDate(new Date(message.getTimeCreated().toInstant().toEpochMilli()))
                    .setSuggestionUrl(message.getJumpUrl())
                    .setOriginalAgrees(positive)
                    .setOriginalDisagrees(negative);

            String[] lines = message.getContentRaw().split("\n");

            if (lines.length >= 1) {
                msg.setSuggestionTitle(message.getContentRaw().split("\n")[0]);
            } else {
                msg.setSuggestionTitle("No Title");
            }

            greenlitMessages.add(msg);
        }

        Logger.info("Finished curating messages at " + new Date());
    }

    public void applyEmoji() {
        Guild guild = Util.getGuild(group.getGuildId());
        if (guild == null) {
            Logger.error("Guild was null wtf happened");
            return;
        }

        TextChannel suggestionChannel = ChannelManager.getChannel(group.getFrom());
        if (suggestionChannel == null) {
            Logger.error("Failed to find suggestion channel!");
            return;
        }

        Emote greenlitEmoji = guild.getEmoteById(Reactions.GREENLIT.getId());
        if (greenlitEmoji == null) {
            Logger.error("Failed to find greenlit emoji!");
            return;
        }

        for (GreenlitMessage msg : greenlitMessages) {
            suggestionChannel.retrieveMessageById(msg.getMessageId()).queue(message -> {
                message.addReaction(greenlitEmoji).queue();
            });
        }

        Logger.info("Applied greenlit emoji to " + greenlitMessages.size() + " message" + (greenlitMessages.size() == 1 ? "" : "s") + " at " + new Date());
    }

    public void send() {
        if (group == null) return;

        TextChannel channel = ChannelManager.getChannel(group.getTo());
        if (channel == null) return;

        for (GreenlitMessage message : greenlitMessages) {
            channel.sendMessageEmbeds(message.getEmbed().build()).queue();
        }
    }

    public void insert() {
        if (!greenlitMessages.isEmpty()) {
            Database.getInstance().insertGreenlitMessages(greenlitMessages);
            Logger.info("Inserted " + greenlitMessages.size() + " greenlit message" + (greenlitMessages.size() == 1 ? "" : "s") + " at " + new Date());
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

    public ChannelGroup getGroup() {
        return group;
    }

    public List<GreenlitMessage> getGreenlitMessages() {
        return greenlitMessages;
    }

}
