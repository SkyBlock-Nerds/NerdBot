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

    private final String modMailChannelId = NerdBotApp.getBot().getConfig().getModMailConfig().getReceivingChannelId();
    private final String modMailRoleMention = "<@&%s>".formatted(NerdBotApp.getBot().getConfig().getModMailConfig().getRoleId());

    @SubscribeEvent
    public void onModMailReceived(MessageReceivedEvent event) throws ExecutionException, InterruptedException {
        if (event.getChannelType() != ChannelType.PRIVATE) {
            return;
        }

        User author = event.getAuthor();
        if (author.isBot() || author.isSystem()) {
            return;
        }

        ForumChannel forumChannel = NerdBotApp.getBot().getJDA().getForumChannelById(modMailChannelId);
        if (forumChannel == null) {
            return;
        }

        Message message = event.getMessage();
        Optional<ThreadChannel> optional = forumChannel.getThreadChannels().stream().filter(threadChannel -> threadChannel.getName().contains(author.getName())).findFirst();
        if (optional.isPresent()) {
            ThreadChannel threadChannel = optional.get();
            if (threadChannel.isArchived()) {
                threadChannel.getManager().setArchived(false).queue();
            }
            threadChannel.sendMessage(modMailRoleMention).queue();
            threadChannel.sendMessage(createMessage(message).build()).queue();
            log.info(author.getName() + " replied to their Mod Mail request (Thread ID: " + threadChannel.getId() + ")");
        } else {
            forumChannel.createForumPost(
                    "[Mod Mail] " + author.getName(),
                    MessageCreateData.fromContent("Received new Mod Mail request from " + author.getAsMention() + "!\n\nUser ID: " + author.getId())
            ).queue(forumPost -> {
                try {
                    ThreadChannel threadChannel = forumPost.getThreadChannel();
                    threadChannel.getManager().setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS).queue();
                    threadChannel.sendMessage(modMailRoleMention).queue();
                    threadChannel.sendMessage(createMessage(message).build()).queue();
                    log.info(author.getName() + " submitted a new Mod Mail request! (Thread ID: " + forumPost.getThreadChannel().getId() + ")");
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

        ThreadChannel threadChannel = event.getChannel().asThreadChannel();
        ForumChannel parent = threadChannel.getParentChannel().asForumChannel();
        if (!parent.getId().equals(modMailChannelId)) {
            return;
        }

        ForumChannel forumChannel = NerdBotApp.getBot().getJDA().getForumChannelById(modMailChannelId);
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
        Message message = event.getMessage();

        MessageCreateBuilder builder = createMessage(message).setContent("**Response from " + author.getName() + " in SkyBlock Nerds:**\n" + message.getContentDisplay());
        requester.openPrivateChannel().flatMap(channel -> channel.sendMessage(builder.build())).queue();
    }

    private MessageCreateBuilder createMessage(Message message) throws ExecutionException, InterruptedException {
        MessageCreateBuilder data = new MessageCreateBuilder();
        data.setContent(String.format("**%s:**\n%s", message.getAuthor().getName(), message.getContentDisplay()));

        // TODO split into another message, but I don't anticipate someone sending a giant essay yet
        if (data.getContent().length() > 2000) {
            data.setContent(data.getContent().substring(0, 1997) + "...");
        }

        if (!message.getAttachments().isEmpty()) {
            List<FileUpload> files = new ArrayList<>();
            for (Message.Attachment attachment : message.getAttachments()) {
                InputStream stream = attachment.getProxy().download().get();
                files.add(FileUpload.fromData(stream, attachment.getFileName()));
            }
            data.setFiles(files);
        }
        return data;
    }
}
