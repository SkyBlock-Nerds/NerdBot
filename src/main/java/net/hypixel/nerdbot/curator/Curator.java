package net.hypixel.nerdbot.curator;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.channel.ChannelGroup;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.DiscordUser;
import net.hypixel.nerdbot.api.database.GreenlitMessage;
import net.hypixel.nerdbot.util.Logger;
import net.hypixel.nerdbot.util.Region;
import net.hypixel.nerdbot.util.Util;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

public class Curator {

    private final int limit;
    private final List<ChannelGroup> groups;
    private final List<GreenlitMessage> greenlitMessages;
    private final List<DiscordUser> users;

    private final Emoji agree, disagree, greenlit;

    private long elapsed;

    /**
     * Initialize a new Curator object with a set limit and {@link ChannelGroup}
     *
     * @param limit  The amount of messages to curate
     * @param groups The list of {@link ChannelGroup} to search through
     */
    public Curator(int limit, List<ChannelGroup> groups) {
        this.limit = limit;
        this.groups = groups;

        greenlitMessages = new ArrayList<>(limit);
        users = new ArrayList<>();

        agree = NerdBotApp.getBot().getJDA().getEmojiById(NerdBotApp.getBot().getConfig().getEmojis().getAgree());
        disagree = NerdBotApp.getBot().getJDA().getEmojiById(NerdBotApp.getBot().getConfig().getEmojis().getDisagree());
        greenlit = NerdBotApp.getBot().getJDA().getEmojiById(NerdBotApp.getBot().getConfig().getEmojis().getGreenlit());
    }

    /**
     * Initialize a new Curator with a limit of 100 messages in a set {@link ChannelGroup}
     *
     * @param group The {@link ChannelGroup} to search through
     */
    public Curator(ChannelGroup group) {
        this(100, List.of(group));
    }

    /**
     * Initialize a new Curator with a limit of 100 messages in a set {@link ChannelGroup}
     *
     * @param limit The amount of messages to curate
     * @param group The {@link ChannelGroup} to search through
     */
    public Curator(int limit, ChannelGroup group) {
        this(limit, List.of(group));
    }

    /**
     * Start the curation process on the selected {@link ChannelGroup} and message limit
     */
    public void curate() {
        if (!Database.getInstance().isConnected()) {
            error("Cannot connect to the database!");
            return;
        }

        if (groups.isEmpty()) {
            error("No groups to curate!");
            return;
        }

        if (limit <= 0) {
            error("Invalid message limit specified! (" + limit + ")");
            return;
        }

        long start = System.currentTimeMillis();
        log(Util.DASHED_LINE);
        log("Curating process started at " + new Date(start));
        log(Util.DASHED_LINE);

        boolean dev = Region.isDev();
        if (dev) log("Since this is a DEV environment we will include all reactions!");

        for (ChannelGroup group : groups) {
            log("[" + group.getName() + "] Starting to curate the suggestions for this group at " + new Date(start) + "!");

            Guild guild = NerdBotApp.getBot().getJDA().getGuildById(group.getGuildId());
            TextChannel suggestionChannel = NerdBotApp.getBot().getJDA().getTextChannelById(group.getFrom());
            TextChannel submissionChannel = NerdBotApp.getBot().getJDA().getTextChannelById(group.getTo());
            if (guild == null || suggestionChannel == null || submissionChannel == null) {
                error("[" + group.getName() + "] Either the guild, suggestion channel, or submission channel is null so I can't continue with this group!");
                continue;
            }

            List<Message> messages = suggestionChannel.getHistory().retrievePast(limit).complete();
            if (messages.isEmpty()) {
                log("[" + group.getName() + "] No messages to curate in this group!");
                continue;
            }

            log("[" + group.getName() + "] Found " + messages.size() + " messages to curate!");
            int count = 0;

            for (Message message : messages) {
                log("[" + group.getName() + "] Curating message " + (++count) + "/" + messages.size() + " by " + message.getAuthor().getAsTag() + "!");

                addEmojiIfMissing(group, message, agree);
                addEmojiIfMissing(group, message, disagree);

                MessageReaction positive = message.getReaction(agree);
                MessageReaction negative = message.getReaction(disagree);
                int realPositive = positive.getCount();
                int realNegative = negative.getCount();

                if (!dev) {
                    realPositive = discountBotAndUserReactions(group, message, positive);
                    realNegative = discountBotAndUserReactions(group, message, negative);
                }

                if (realPositive == 0 && realNegative == 0) {
                    log("[" + group.getName() + "] [" + message.getId() + "] Skipping because it has no positive and negative reactions!");
                    continue;
                }

                getAndUpdateUserReactions(group, message, positive, negative);

                if (message.getReaction(greenlit) != null) {
                    checkGreenlit(group, message, realPositive, realNegative);
                    continue;
                }

                double ratio = getRatio(realPositive, realNegative);
                double requiredRatio = NerdBotApp.getBot().getConfig().getPercentage();
                String ratioMessage = "[" + group.getName() + "] [" + message.getId() + "] Reaction ratio is "
                        + Util.DECIMAL_FORMAT.format(ratio) + "% (" + realPositive + " positive / " + realNegative + " negative)";
                if (ratio < requiredRatio) {
                    ratioMessage += ". This is below the minimum threshold of " + Util.DECIMAL_FORMAT.format(requiredRatio) + "%!";
                    log(ratioMessage);
                    continue;
                }

                ratioMessage += ". Greenlighting this message!";
                log(ratioMessage);

                message.addReaction(greenlit).queue();

                GreenlitMessage msg = createGreenlitMessage(group, message, realPositive, realNegative);
                sendGreenlitToChannel(submissionChannel, group, msg);
            }
        }

        if (!users.isEmpty()) Database.getInstance().updateUsers(users);

        long end = System.currentTimeMillis();
        elapsed = end - start;

        log(Util.DASHED_LINE);
        log("Curating process finished at " + new Date(end) + ". Elapsed time: " + elapsed + "ms");
        log(Util.DASHED_LINE);
    }

    /**
     * Send the greenlit message to the submission channel
     *
     * @param channel      The {@link TextChannel} to send the message to
     * @param channelGroup The {@link ChannelGroup} being curated
     * @param message      The {@link GreenlitMessage} to send
     */
    private void sendGreenlitToChannel(TextChannel channel, ChannelGroup channelGroup, GreenlitMessage message) {
        channel.sendMessageEmbeds(message.getEmbed().build()).queue(message1 -> {
            message.setGreenlitMessageId(message1.getId());
            Database.getInstance().insertGreenlitMessage(message);
            log("[" + channelGroup.getName() + "] [" + message.getId() + "] Greenlit message sent to " + channel.getName() + " and inserted into database");
            greenlitMessages.add(message);
        });
    }

    /**
     * Add an emoji to a message if it doesn't already exist
     *
     * @param group   The {@link ChannelGroup} to search through
     * @param message The {@link Message} to add the emoji to
     * @param emoji   The {@link Emoji} to add to the message
     */
    private void addEmojiIfMissing(ChannelGroup group, Message message, @NotNull Emoji emoji) {
        if (message.getReaction(emoji) == null) {
            log("[" + group.getName() + "] [" + message.getId() + "] No reaction found, adding one!");
            message.addReaction(emoji).queue();
        }
    }

    /**
     * Create a new {@link GreenlitMessage} from a {@link Message}
     *
     * @param message  The {@link Message} to create the {@link GreenlitMessage} from
     * @param positive The positive amount of {@link MessageReaction}
     * @param negative The negative amount of {@link MessageReaction}
     * @return The new {@link GreenlitMessage}
     */
    private GreenlitMessage createGreenlitMessage(ChannelGroup group, Message message, int positive, int negative) {
        return new GreenlitMessage()
                .setUserId(message.getAuthor().getId())
                .setMessageId(message.getId())
                .setTags(getTags(message.getContentRaw()))
                .setSuggestionTitle(Util.getFirstLine(message))
                .setSuggestionContent(message.getContentRaw())
                .setSuggestionDate(new Date(message.getTimeCreated().toInstant().toEpochMilli()))
                .setSuggestionUrl(message.getJumpUrl())
                .setAgrees(positive)
                .setDisagrees(negative)
                .setChannelGroupName(group.getName());
    }

    /**
     * Get all tags that a message contains in the first line
     *
     * @param message The {@link Message} to get the tags from
     * @return A list of tags
     */
    private List<String> getTags(String message) {
        String firstLine = message.split("\n")[0];
        Matcher matcher = Util.SUGGESTION_TITLE_REGEX.matcher(firstLine);
        List<String> tags = new ArrayList<>();

        while (matcher.find())
            tags.add(matcher.group(1));

        return tags;
    }

    /**
     * Remove all reactions from a message by a user or bot
     *
     * @param message  The {@link Message} to remove the reactions from
     * @param reaction The {@link MessageReaction} to check and remove from
     */
    private int discountBotAndUserReactions(ChannelGroup group, Message message, MessageReaction reaction) {
        AtomicInteger total = new AtomicInteger(reaction.getCount());

        reaction.retrieveUsers().stream().filter(user -> user.getId().equals(message.getAuthor().getId()) || user.getId().equals(NerdBotApp.getBot().getJDA().getSelfUser().getId())).forEach(user -> {
            log("[" + group.getName() + "] [" + message.getId() + "] Discounting " + user.getAsTag() + " from the " + reaction.getEmoji().getName() + " reaction!");
            total.getAndDecrement();
        });

        return total.get();
    }

    /**
     * Grab and update a {@link DiscordUser}'s positive and negative reaction count
     *
     * @param group    The {@link ChannelGroup} to search through
     * @param message  The {@link Message} to get the reactions from
     * @param positive The {@link MessageReaction} to check and update the positive count
     * @param negative The {@link MessageReaction} to check and update the negative count
     */
    private void getAndUpdateUserReactions(ChannelGroup group, Message message, MessageReaction positive, MessageReaction negative) {
        positive.retrieveUsers().complete().forEach(user -> {
            if (user.isBot() || (!Region.isDev() && user.getId().equals(message.getAuthor().getId()))) return;

            DiscordUser discordUser = findUser(user.getId());

            if (!discordUser.getAgrees().contains(message.getId())) {
                discordUser.getAgrees().add(message.getId());
                log("[" + group.getName() + "] [" + message.getId() + "] Added message to " + discordUser.getDiscordId() + "'s agrees!");
            }
        });

        negative.retrieveUsers().complete().forEach(user -> {
            if (user.isBot() || (!Region.isDev() && user.getId().equals(message.getAuthor().getId()))) return;

            DiscordUser discordUser = findUser(user.getId());

            if (!discordUser.getDisagrees().contains(message.getId())) {
                discordUser.getDisagrees().add(message.getId());
                log("[" + group.getName() + "] [" + message.getId() + "] Added message to " + discordUser.getDiscordId() + "'s disagrees!");
            } else {
                log("[" + group.getName() + "] [" + message.getId() + "] Message already added to " + discordUser.getDiscordId() + "'s disagrees!");
            }
        });
    }

    /**
     * Check an existing {@link GreenlitMessage} for different reaction values and update it if it exists
     *
     * @param group     The {@link ChannelGroup} to search through
     * @param message   The {@link Message} to check for
     * @param agrees    The new number of agrees the message has
     * @param disagrees The new number of disagrees the message has
     */
    private void checkGreenlit(ChannelGroup group, Message message, int agrees, int disagrees) {
        log("[" + group.getName() + "] [" + message.getId() + "] This message is already greenlit! Checking to see if the reaction count has changed");

        Guild guild = NerdBotApp.getBot().getJDA().getGuildById(group.getGuildId());
        if (guild == null) {
            log("[" + group.getName() + "] [" + message.getId() + "] Could not find guild " + group.getGuildId());
            return;
        }

        TextChannel channel = guild.getTextChannelById(group.getTo());
        if (channel == null) {
            log("[" + group.getName() + "] [" + message.getId() + "] Could not find channel " + group.getTo());
            return;
        }

        GreenlitMessage greenlitMessage = Database.getInstance().getGreenlitMessage(message.getId());

        if (greenlitMessage == null) {
            greenlitMessage = createGreenlitMessage(group, message, agrees, disagrees);

            Database.getInstance().insertGreenlitMessage(greenlitMessage);
            log("[" + group.getName() + "] [" + message.getId() + "] Created new greenlit message because it wasn't found in the database");

            if (greenlitMessage.getGreenlitMessageId() != null) {
                if (channel.retrieveMessageById(greenlitMessage.getGreenlitMessageId()).complete() != null) {
                    sendGreenlitToChannel(channel, group, greenlitMessage);
                }
            } else {
                GreenlitMessage finalGreenlitMessage = greenlitMessage;
                channel.sendMessageEmbeds(greenlitMessage.getEmbed().build()).queue(msg -> {
                    finalGreenlitMessage.setGreenlitMessageId(msg.getId());
                    Database.getInstance().updateGreenlitMessage(finalGreenlitMessage);
                    log("[" + group.getName() + "] [" + message.getId() + "] Updated greenlit message ID to " + msg.getId());
                });
            }
        }

        if (greenlitMessage.getAgrees() == agrees && greenlitMessage.getDisagrees() == disagrees) {
            log("[" + group.getName() + "] [" + message.getId() + "] Reaction count has not changed");
            return;
        }

        log("[" + group.getName() + "] [" + message.getId() + "] Updating record in the database with the new reaction count! "
                + "(" + greenlitMessage.getAgrees() + "->" + agrees + " positive, "
                + greenlitMessage.getDisagrees() + "->" + disagrees + " negative)");
        greenlitMessage.setAgrees(agrees);
        greenlitMessage.setDisagrees(disagrees);

        if (greenlitMessage.getGreenlitMessageId() != null) {
            greenlitMessage.setSuggestionContent(message.getContentRaw());
            channel.retrieveMessageById(greenlitMessage.getGreenlitMessageId()).complete().editMessageEmbeds(greenlitMessage.getEmbed().build()).queue();
        }

        Database.getInstance().updateGreenlitMessage(greenlitMessage);
    }

    /**
     * Find a {@link DiscordUser} in the database by their Discord ID
     *
     * @param id The Discord ID to search for
     * @return The {@link DiscordUser} if found, null otherwise
     */
    private DiscordUser findUser(String id) {
        DiscordUser discordUser;

        if (users.stream().anyMatch(u -> u.getDiscordId().equals(id))) {
            discordUser = users.stream().filter(u -> u.getDiscordId().equals(id)).findFirst().get();
        } else {
            discordUser = new DiscordUser(id, null, new ArrayList<>(), new ArrayList<>());
            users.add(discordUser);
        }

        return discordUser;
    }

    /**
     * Get the ratio of positive to negative reactions for a {@link Message}
     *
     * @param positive The number of positive reactions
     * @param negative The number of negative reactions
     * @return The ratio of positive to negative reactions
     */
    private double getRatio(int positive, int negative) {
        if (positive == 0 && negative == 0) return 0;
        return (double) positive / (positive + negative) * 100;
    }

    private void log(String message) {
        Logger.info("[Curator] " + message);
    }

    private void error(String message) {
        Logger.error("[Curator] " + message);
    }

    public int getLimit() {
        return limit;
    }

    public long getElapsedTime() {
        return elapsed;
    }

    public List<ChannelGroup> getGroups() {
        return groups;
    }

    public List<DiscordUser> getUsers() {
        return users;
    }

    public List<GreenlitMessage> getGreenlitMessages() {
        return greenlitMessages;
    }

}
