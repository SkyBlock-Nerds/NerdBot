package net.hypixel.nerdbot.listener;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.hypixel.nerdbot.NerdBotApp;

import java.util.List;
import java.util.Optional;

@Log4j2
public class ModMailListener {

    private final String receivingChannelId = NerdBotApp.getBot().getConfig().getModMailConfig().getReceivingChannelId();

    @SubscribeEvent
    public void onPrivateMessage(MessageReceivedEvent event) {
        if (event.getChannelType() != ChannelType.PRIVATE) {
            return;
        }

        Message message = event.getMessage();
        User author = event.getAuthor();

        if (author.isBot() || author.isSystem()) {
            return;
        }

        ForumChannel forumChannel = NerdBotApp.getBot().getJDA().getForumChannelById(receivingChannelId);
        if (forumChannel == null) {
            return;
        }

        Optional<ThreadChannel> optional = forumChannel.getThreadChannels().stream().filter(threadChannel -> threadChannel.getName().contains(author.getName())).findFirst();
        if (optional.isPresent()) {
            ThreadChannel threadChannel = optional.get();
            if (threadChannel.isArchived()) {
                threadChannel.getManager().setArchived(false).queue();
                log.info("Received new request from old thread " + threadChannel.getName());
                threadChannel.sendMessage("Received new request from old thread").queue();
                // TODO send message of new request from old thread
            }
            threadChannel.sendMessage("**" + author.getName() + ":**\n" + message.getContentDisplay()).queue();
        } else {
            forumChannel.createForumPost("Mod Mail - " + author.getName(), MessageCreateData.fromContent("Received new Mod Mail request from " + author.getAsMention() + "!\n\n**Request:**\n" + message.getContentDisplay())).queue(forumPost -> {
                log.info(author.getName() + " submitted a new mod mail request! (ID: " + forumPost.getThreadChannel().getId() + ")");
                forumPost.getThreadChannel().getManager().setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS).queue();
            });
        }
    }

    @SubscribeEvent
    public void onModMailResponse(MessageReceivedEvent event) throws RateLimitedException {
        if (event.getChannelType() != ChannelType.GUILD_PUBLIC_THREAD) {
            return;
        }

        Message message = event.getMessage();
        ThreadChannel threadChannel = event.getChannel().asThreadChannel();
        ForumChannel parent = threadChannel.getParentChannel().asForumChannel();

        if (!parent.getId().equals(receivingChannelId)) {
            return;
        }

        ForumChannel forumChannel = NerdBotApp.getBot().getJDA().getForumChannelById(receivingChannelId);
        if (forumChannel == null) {
            return;
        }

        List<Message> allMessages = threadChannel.getIterableHistory().complete(true);
        Message firstPost = allMessages.get(allMessages.size() - 1);
        User requester = firstPost.getMentions().getUsers().get(0);

        User author = event.getAuthor();
        if (author.isBot() || author.isSystem()) {
            return;
        }

        requester.openPrivateChannel().flatMap(channel -> channel.sendMessage("**Response from " + author.getName() + " in SkyBlock Nerds:**\n" + message.getContentDisplay())).queue();
    }
}
