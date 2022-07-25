package net.hypixel.nerdbot.curator;

import net.dv8tion.jda.api.entities.*;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.channel.ChannelGroup;
import net.hypixel.nerdbot.api.channel.ChannelManager;
import net.hypixel.nerdbot.api.channel.Reactions;
import net.hypixel.nerdbot.api.config.BotConfig;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.DiscordUser;
import net.hypixel.nerdbot.api.database.GreenlitMessage;
import net.hypixel.nerdbot.util.Logger;
import net.hypixel.nerdbot.util.Region;
import net.hypixel.nerdbot.util.Util;

import java.util.*;
import java.util.regex.Matcher;

public class Curator {

    private final int limit;
    private final ChannelGroup group;
    private final List<GreenlitMessage> greenlitMessages;
    private final List<DiscordUser> users;

    /**
     * Initialize a new Curator object with a set limit and {@link ChannelGroup}
     *
     * @param limit The amount of messages to curate
     * @param group The {@link ChannelGroup} to search through
     */
    public Curator(int limit, ChannelGroup group) {
        this.limit = limit;
        this.group = group;
        greenlitMessages = new ArrayList<>(limit);
        users = new ArrayList<>();
    }

    /**
     * Initialize a new Curator with a limit of 100 messages in a set {@link ChannelGroup}
     *
     * @param group The {@link ChannelGroup} to search through
     */
    public Curator(ChannelGroup group) {
        this(100, group);
    }

    /**
     * Start the curation process on the selected {@link ChannelGroup} and message limit
     */
    public void curate() {
        if (!Database.getInstance().isConnected()) {
            Logger.error("[Curator] Cannot connect to the database!");
            return;
        }
        TextChannel textChannel = ChannelManager.getChannel(group.getFrom());
        if (textChannel == null) return;

        MessageHistory history = textChannel.getHistory();
        List<Message> messages = history.retrievePast(limit).complete();

        long start = System.currentTimeMillis();
        log("Starting suggestion curation at " + new Date());

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                textChannel.sendTyping().queue();
            }
        };
        Timer timer = new Timer();
        timer.schedule(timerTask, 0, 10_000);

        for (Message message : messages) {
            if (message.getAuthor().isBot() || message.getReactionById(Reactions.GREENLIT.getId()) != null)
                continue;

            Emote agree = NerdBotApp.getBot().getJDA().getEmoteById(Reactions.AGREE.getId());
            Emote disagree = NerdBotApp.getBot().getJDA().getEmoteById(Reactions.DISAGREE.getId());
            if (agree != null && message.getReactionById(Reactions.AGREE.getId()) == null)
                message.addReaction(agree).queue();

            if (disagree != null && message.getReactionById(Reactions.DISAGREE.getId()) == null)
                message.addReaction(disagree).queue();

            int positive = 0, negative = 0;
            for (MessageReaction reaction : message.getReactions()) {
                if (reaction.getReactionEmote().isEmoji())
                    continue;

                // We remove 1 from each agree and disagree because of the bots reaction
                // And also remove one from the person who suggested it
                // Maybe could even remove the actual reaction from the person as a visual indication
                if (reaction.getReactionEmote().getId().equals(Reactions.AGREE.getId())) {
                    positive = reaction.getCount() - 1;
                    if (Region.isProduction() && reaction.retrieveUsers().stream().map(ISnowflake::getId).anyMatch(s -> s.equals(message.getAuthor().getId()))) {
                        positive--;
                    }
                }

                if (reaction.getReactionEmote().getId().equals(Reactions.DISAGREE.getId())) {
                    negative = reaction.getCount() - 1;
                    if (Region.isProduction() && reaction.retrieveUsers().stream().map(ISnowflake::getId).anyMatch(s -> s.equals(message.getAuthor().getId()))) {
                        negative--;
                    }
                }

                // Track reactions for each user and save them into the database
                reaction.retrieveUsers().forEach(user -> {
                    if (user.isBot()) return;

                    log("User " + user.getAsTag() + " reacted with " + reaction.getReactionEmote().getName() + " to message " + message.getId());

                    DiscordUser discordUser = findUser(user.getId());
                    if (discordUser == null)
                        discordUser = new DiscordUser(user.getId(), null, Collections.emptyList(), Collections.emptyList());

                    if (!users.contains(discordUser))
                        users.add(discordUser);

                    switch (reaction.getReactionEmote().getName()) {
                        case "yes" -> {
                            if (!discordUser.getAgrees().contains(message.getId())) {
                                discordUser.getAgrees().add(message.getId());
                                log("Total agrees for " + discordUser.getDiscordId() + " is now " + discordUser.getAgrees().size());
                            }
                        }
                        case "no" -> {
                            if (!discordUser.getDisagrees().contains(message.getId())) {
                                discordUser.getDisagrees().add(message.getId());
                                log("Total disagrees for " + discordUser.getDiscordId() + " is now " + discordUser.getDisagrees().size());
                            }
                        }
                    }

                    // TODO figure out a better way for this
                    /*Date lastReactionDate = discordUser.getLastKnownActivityDate();
                    Date date = new Date();

                    if (lastReactionDate == null || lastReactionDate.before(date)) {
                        log("User " + user.getAsTag() + "'s last reaction was " + lastReactionDate + ". Setting it to " + date);
                        discordUser.setLastKnownActivityDate(date);
                    }*/
                });
            }

            BotConfig config = NerdBotApp.getBot().getConfig();
            if (positive == 0 && negative == 0 || positive < config.getMinimumThreshold()) {
                log("Message " + message.getId() + " is below the minimum threshold! (" + positive + "/" + negative + ") (min threshold: " + config.getMinimumThreshold() + ")");
                continue;
            }

            double ratio = getRatio(positive, negative);
            log("Message " + message.getId() + " has a ratio of " + ratio + "%");
            if (ratio < config.getPercentage()) continue;

            // Get the title and tags of the suggestion to save and display
            // A suggestion can have multiple tags e.g. "[MINING] [SKILL] Suggestion Title"
            String firstLine = message.getContentRaw().split("\n")[0];
            Matcher matcher = Util.SUGGESTION_TITLE_REGEX.matcher(firstLine);
            List<String> tags = new ArrayList<>();
            while (matcher.find()) {
                tags.add(matcher.group(1));
                log("Found tag '" + matcher.group(1) + "' in message " + message.getId());
            }

            GreenlitMessage msg = new GreenlitMessage()
                    .setUserId(message.getAuthor().getId())
                    .setMessageId(message.getId())
                    .setTags(tags)
                    .setSuggestionContent(message.getContentRaw())
                    .setSuggestionDate(new Date(message.getTimeCreated().toInstant().toEpochMilli()))
                    .setSuggestionUrl(message.getJumpUrl())
                    .setOriginalAgrees(positive)
                    .setOriginalDisagrees(negative);
            String[] lines = message.getContentRaw().split("\n");
            msg.setSuggestionTitle(lines.length >= 1 ? lines[0] : "No Title");
            greenlitMessages.add(msg);
            log("Added suggestion " + message.getId() + " created by " + message.getAuthor().getAsTag() + " to the greenlit collection");
        }

        if (!users.isEmpty()) Database.getInstance().updateUsers(users);
        if (!getGreenlitMessages().isEmpty()) {
            applyEmoji();
            insertIntoDatabase();
            sendGreenlitToChannel();
        }
        long end = System.currentTimeMillis();
        log("Finished curating messages at " + new Date() + ". Took " + (end - start) + "ms");
        timer.cancel();
    }

    /**
     * Apply the greenlit emoji to all saved greenlit messages
     */
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
        for (GreenlitMessage msg : greenlitMessages)
            suggestionChannel.retrieveMessageById(msg.getMessageId()).queue(message -> message.addReaction(greenlitEmoji).queue());
        log("Applied greenlit emoji to " + greenlitMessages.size() + " message" + (greenlitMessages.size() == 1 ? "" : "s") + " at " + new Date());
    }

    /**
     * Send all saved greenlit messages to the {@link ChannelGroup#getTo()}
     */
    public void sendGreenlitToChannel() {
        if (group == null) return;
        TextChannel channel = ChannelManager.getChannel(group.getTo());
        if (channel == null) return;

        Emote agree = NerdBotApp.getBot().getJDA().getEmoteById(Reactions.AGREE.getId());
        Emote disagree = NerdBotApp.getBot().getJDA().getEmoteById(Reactions.DISAGREE.getId());
        for (GreenlitMessage message : greenlitMessages) {
            channel.sendMessageEmbeds(message.getEmbed().build()).queue(msg -> {
                if (agree != null) msg.addReaction(agree).queue();
                if (disagree != null) msg.addReaction(disagree).queue();
            });
        }
    }

    /**
     * Insert all saved greenlit messages into the database.
     */
    public void insertIntoDatabase() {
        if (!greenlitMessages.isEmpty()) {
            Database.getInstance().insertGreenlitMessages(greenlitMessages);
            log("Inserted " + greenlitMessages.size() + " greenlit message" + (greenlitMessages.size() == 1 ? "" : "s") + " at " + new Date());
        } else {
            log("No greenlit messages to insert!");
        }
    }

    private DiscordUser findUser(String id) {
        DiscordUser user = null;
        for (DiscordUser discordUser : users)
            if (discordUser.getDiscordId().equals(id)) user = discordUser;

        if (user == null) user = Database.getInstance().getUser(id);
        return user;
    }

    private double getRatio(int positive, int negative) {
        return (double) positive / (positive + negative) * 100;
    }

    private void log(String message) {
        Logger.info("[Curator] " + message);
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
