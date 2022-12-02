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
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.hypixel.nerdbot.NerdBotApp;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Log4j2
public class ModMailListener {

    public static final String MOD_MAIL_CHANNEL_ID = NerdBotApp.getBot().getConfig().getModMailConfig().getReceivingChannelId();

    @SubscribeEvent
    public void onModMailReceived(MessageReceivedEvent event) throws ExecutionException, InterruptedException {
        if (event.getChannelType() != ChannelType.PRIVATE) {
            return;
        }

        Message message = event.getMessage();
        User author = event.getAuthor();
        if (author.isBot() || author.isSystem()) {
            return;
        }

        ForumChannel forumChannel = NerdBotApp.getBot().getJDA().getForumChannelById(MOD_MAIL_CHANNEL_ID);
        if (forumChannel == null) {
            return;
        }

        String msg = String.format("**%s:**\n%s", author.getName(), message.getContentDisplay());
        // TODO send another message with the remaining contents, or send a file with the text maybe?
        if (msg.length() > 2000) {
            msg = msg.substring(0, 2000);
        }

        Optional<ThreadChannel> optional = forumChannel.getThreadChannels().stream().filter(threadChannel -> threadChannel.getName().contains(author.getName())).findFirst();
        if (optional.isPresent()) {
            log.info(author.getName() + " replied to their Mod Mail request");
            ThreadChannel threadChannel = optional.get();
            if (threadChannel.isArchived()) {
                threadChannel.getManager().setArchived(false).queue();
                log.info("Received new request from old thread " + threadChannel.getName());
                threadChannel.sendMessage("Received new request from old thread").queue();
                // TODO send message of new request from old thread
            }

            MessageCreateBuilder data = createMessage(message).setContent(msg);
            threadChannel.sendMessage(data.build()).queue();
        } else {
            String initial = "Received new Mod Mail request from " + author.getAsMention() + "!\n\nUser ID: " + author.getId() + "\nThread ID: ";
            forumChannel.createForumPost("[Mod Mail] " + author.getName(), MessageCreateData.fromContent("Received new Mod Mail request from " + author.getAsMention() + "!")).queue(forumPost -> {
                try {
                    forumPost.getMessage().editMessage(initial + forumPost.getMessage().getId()).queue();
                    log.info(author.getName() + " submitted a new mod mail request! (ID: " + forumPost.getThreadChannel().getId() + ")");
                    forumPost.getThreadChannel().getManager().setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS).queue();
                    MessageCreateBuilder builder = createMessage(message);
                    forumPost.getThreadChannel().sendMessage(builder.build()).queue();
                } catch (ExecutionException | InterruptedException e) {
                    author.openPrivateChannel().flatMap(channel -> channel.sendMessage("I wasn't able to send your request! Please try again later.")).queue();
                    throw new RuntimeException(e);
                }
            });
        }
    }

    @SubscribeEvent
    public void onModMailResponse(MessageReceivedEvent event) throws RateLimitedException, ExecutionException, InterruptedException {
        if (event.getChannelType() != ChannelType.GUILD_PUBLIC_THREAD) {
            return;
        }

        Message message = event.getMessage();
        ThreadChannel threadChannel = event.getChannel().asThreadChannel();
        ForumChannel parent = threadChannel.getParentChannel().asForumChannel();
        if (!parent.getId().equals(MOD_MAIL_CHANNEL_ID)) {
            return;
        }

        ForumChannel forumChannel = NerdBotApp.getBot().getJDA().getForumChannelById(MOD_MAIL_CHANNEL_ID);
        if (forumChannel == null) {
            return;
        }

        User author = event.getAuthor();
        if (author.isBot() || author.isSystem()) {
            return;
        }

        List<Message> allMessages = threadChannel.getIterableHistory().complete(true);
        Message firstPost = allMessages.get(allMessages.size() - 1);
        User requester = firstPost.getMentions().getUsers().get(0);
        MessageCreateBuilder builder = createMessage(message);
        builder.setContent("**Response from " + author.getName() + " in SkyBlock Nerds:**\n" + message.getContentDisplay());
        requester.openPrivateChannel().flatMap(channel -> channel.sendMessage(builder.build())).queue();
    }

    private MessageCreateBuilder createMessage(Message message) throws ExecutionException, InterruptedException {
        MessageCreateBuilder data = new MessageCreateBuilder();
        data.setContent(message.getContentDisplay());
        if (!message.getAttachments().isEmpty()) {
            List<FileUpload> files = new ArrayList<>();
            for (Message.Attachment attachment : message.getAttachments()) {
                InputStream stream = attachment.getProxy().download().get();
                files.add(FileUpload.fromData(stream, attachment.getFileName()));
            }
            log.info("files: " + files.size());
            data.setFiles(files);
        }
        return data;
    }
}
