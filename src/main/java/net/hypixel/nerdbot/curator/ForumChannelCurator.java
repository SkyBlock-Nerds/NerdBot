package net.hypixel.nerdbot.curator;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.internal.entities.ForumTagImpl;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.channel.ChannelManager;
import net.hypixel.nerdbot.api.curator.Curator;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.database.user.DiscordUser;
import net.hypixel.nerdbot.util.Users;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Log4j2
public class ForumChannelCurator extends Curator<ForumChannel> {

    public ForumChannelCurator(boolean readOnly) {
        super(readOnly);
    }

    @Override
    public List<GreenlitMessage> curate(ForumChannel forumChannel) {
        List<GreenlitMessage> output = new ArrayList<>();
        setStartTime(System.currentTimeMillis());

        log.info("Curating forum channel: " + forumChannel.getName() + " (Channel ID: " + forumChannel.getId() + ")");

        List<ThreadChannel> threads = forumChannel.getThreadChannels()
                .stream()
                .filter(threadChannel -> threadChannel.getAppliedTags().stream().anyMatch(tag -> !tag.getName().equalsIgnoreCase("greenlit")))
                .toList();
        for (ThreadChannel thread : threads) {
            log.info("Curating thread: " + thread.getName() + " (Thread ID: " + thread.getId() + ")");

            try {
                // This is a stupid way to do it but it's the only way that works right now
                List<Message> allMessages = thread.getIterableHistory().complete(true);
                Message firstPost = allMessages.get(allMessages.size() - 1);
                Emoji agreeEmoji = getJDA().getEmojiById(NerdBotApp.getBot().getConfig().getEmojiConfig().getAgree());
                Emoji disagreeEmoji = getJDA().getEmojiById(NerdBotApp.getBot().getConfig().getEmojiConfig().getDisagree());

                DiscordUser discordUser = Database.getInstance().getOrAddUserToCache(firstPost.getAuthor().getId());

                if (discordUser.getLastActivity().getLastSuggestionDate() < firstPost.getTimeCreated().toInstant().toEpochMilli()) {
                    discordUser.getLastActivity().setLastSuggestionDate(firstPost.getTimeCreated().toInstant().toEpochMilli());
                    log.info("Updating last suggestion time for " + firstPost.getAuthor().getName() + " to " + firstPost.getTimeCreated().toEpochSecond());
                }

                if (agreeEmoji == null || disagreeEmoji == null) {
                    log.error("Couldn't find the agree or disagree emoji, time to yell!");
                    if (ChannelManager.getLogChannel() != null) {
                        ChannelManager.getLogChannel().sendMessage(Users.getUser(Users.AERH).getAsMention() + " I couldn't find the agree or disagree emoji for some reason, check logs!").queue();
                    }
                    break;
                }

                // Add the reactions if they're missing
                // Theoretically the agree reaction should be there by default but lets check anyway just in case
                int agreeReaction = firstPost.getReaction(agreeEmoji) == null ? 0 : Objects.requireNonNull(firstPost.getReaction(agreeEmoji)).getCount();
                int disagreeReaction = firstPost.getReaction(disagreeEmoji) == null ? 0 : Objects.requireNonNull(firstPost.getReaction(disagreeEmoji)).getCount();

                if (agreeReaction < NerdBotApp.getBot().getConfig().getMinimumThreshold()) {
                    log.info("Post " + firstPost.getId() + " doesn't have enough agree reactions, skipping...");
                    continue;
                }

                // Get the ratio of reactions and greenlight it if it's over the threshold
                double ratio = getRatio(agreeReaction, disagreeReaction);
                if (ratio >= NerdBotApp.getBot().getConfig().getPercentage()) {
                    GreenlitMessage greenlitMessage = GreenlitMessage.builder()
                            .agrees(agreeReaction)
                            .disagrees(disagreeReaction)
                            .messageId(firstPost.getId())
                            .suggestionUrl(firstPost.getJumpUrl())
                            .suggestionTitle(thread.getName())
                            .suggestionTimestamp(thread.getTimeCreated().toInstant().toEpochMilli())
                            .suggestionContent(firstPost.getContentRaw())
                            .build();

                    log.info("Greenlighting thread '" + thread.getName() + "' (Thread ID: " + thread.getId() + ") with a ratio of " + ratio + "%");
                    thread.getManager().setAppliedTags(new ForumTagImpl(NerdBotApp.getBot().getConfig().getTagConfig().getGreenlit())).complete(true);
                    output.add(greenlitMessage);
                }
            } catch (RateLimitedException exception) {
                log.info("Currently being rate limited so the curation process has to stop!");
                break;
            }
        }

        setEndTime(System.currentTimeMillis());
        log.info("Curated forum channel: " + forumChannel.getName() + " (Channel ID: " + forumChannel.getId() + ") in " + (getEndTime() - getStartTime()) + "ms");

        return output;
    }
}
