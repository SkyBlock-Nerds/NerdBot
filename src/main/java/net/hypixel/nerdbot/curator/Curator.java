package net.hypixel.nerdbot.curator;

import net.dv8tion.jda.api.entities.*;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.channel.Reactions;
import net.hypixel.nerdbot.config.BotConfig;
import net.hypixel.nerdbot.util.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Curator {

    private final int limit;

    private final TextChannel channel;

    private final Set<Message> greenlitMessages;

    public Curator(int limit, TextChannel channel) {
        this.limit = limit;
        this.channel = channel;
        greenlitMessages = new HashSet<>(limit);
    }

    public void curate() {
        MessageHistory history = channel.getHistory();
        List<Message> messages = history.retrievePast(limit).complete();

        for (Message message : messages) {
            if (message.getAuthor().isBot()) {
                continue;
            }

            if (message.getReactionById(Reactions.GREENLIT.getId()) != null) {
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

            greenlitMessages.add(message);
        }
    }

    public void applyEmoji() {
        Guild guild = channel.getGuild();
        Emote greenlit = guild.getEmoteById(Reactions.GREENLIT.getId());
        for (Message message : greenlitMessages) {
            message.addReaction(greenlit).queue();
        }
        Logger.info("Applied greenlit emoji to " + greenlitMessages.size() + " messages");
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

    public Set<Message> getGreenlitMessages() {
        return greenlitMessages;
    }
}
