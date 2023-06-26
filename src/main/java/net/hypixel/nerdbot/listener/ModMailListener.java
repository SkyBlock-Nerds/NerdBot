package net.hypixel.nerdbot.listener;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.util.Util;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

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

        if (modMailRoleMention == null) {
            return;
        }

        Message message = event.getMessage();
        Optional<ThreadChannel> optional = forumChannel.getThreadChannels().stream().filter(threadChannel -> threadChannel.getName().contains(author.getName()) || threadChannel.getName().contains(author.getId())).findFirst();
        if (optional.isPresent()) {
            ThreadChannel threadChannel = optional.get();

            if (threadChannel.isArchived()) {
                threadChannel.getManager().setArchived(false).complete();
                // WiViW: This means that the user likely has sent a new request to Staff/Grapes (That way it's not always responding with this.), meaning they should get a response it is being looked at (Visible feedback to the user, rather than nothing it currently is).
                MessageCreateBuilder builder = createMessage(message).setContent("Thank you for contacting Mod Mail, we will get back with your request shortly.");
                event.getAuthor().openPrivateChannel().flatMap(channel -> channel.sendMessage(builder.build())).queue();
            }

            if (!threadChannel.getName().contains(author.getName()) || !threadChannel.getName().contains(author.getId())) {
                threadChannel.getManager().setName("[Mod Mail] " + author.getName() + " (" + author.getId() + ")").complete();
            }

            threadChannel.sendMessage(modMailRoleMention).queue();
            threadChannel.sendMessage(createMessage(message).build()).queue();
            log.info(author.getName() + " replied to their Mod Mail request (Thread ID: " + threadChannel.getId() + ")");
        } else {
            forumChannel.createForumPost(
                    "[Mod Mail] " + author.getName() + " (" + author.getId() + ")",
                    MessageCreateData.fromContent("Received new Mod Mail request from " + author.getAsMention() + "!\n\nUser ID: " + author.getId())
            ).queue(forumPost -> {
                ThreadChannel threadChannel = forumPost.getThreadChannel();
                List<Member> roleMembers = threadChannel.getGuild().getMembersWithRoles(Util.getRole("Mod Mail"));
                AtomicInteger count = new AtomicInteger();

                threadChannel.getManager().setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS).queue();
                threadChannel.getGuild().getMembersWithRoles(Util.getRole("Mod Mail")).forEach(member -> threadChannel.addThreadMember(member).queue(unused -> {
                    if (count.incrementAndGet() == roleMembers.size()) {
                        try {
                            threadChannel.sendMessage(modMailRoleMention).queue();
                            threadChannel.sendMessage(createMessage(message).build()).queue();
                        } catch (ExecutionException | InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }));
            });
        }
    }

    @SubscribeEvent
    public void onModMailResponse(MessageReceivedEvent event) throws ExecutionException, InterruptedException {
        if (event.getChannelType() != ChannelType.GUILD_PUBLIC_THREAD) {
            return;
        }

        ThreadChannel threadChannel = event.getChannel().asThreadChannel();
        if (!(threadChannel.getParentChannel() instanceof ForumChannel)) {
            return;
        }

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

        Message message = event.getMessage();

        // Stuffy: Check if message starts with ? and if so, ignore it, log it and send a message to the thread channel.
        if (message.getContentRaw().startsWith("?")) {
            Emoji emoji = Emoji.fromUnicode("U+1F92B");
            message.addReaction(emoji).queue();
            log.info(author.getName() + " sent a hidden message (Thread ID: " + threadChannel.getId() + ")");
            return;
        }

        String userId = threadChannel.getName().substring(threadChannel.getName().lastIndexOf("(") + 1, threadChannel.getName().lastIndexOf(")"));
        User requester = NerdBotApp.getBot().getJDA().getUserById(userId);
        if (requester == null) {
            log.error("Unable to find user with ID " + userId + " for thread " + threadChannel.getId() + "!");
            return;
        }

        MessageCreateBuilder builder = createMessage(message).setContent("**Response from " + author.getName() + " in SkyBlock Nerds:**\n" + message.getContentDisplay());
        requester.openPrivateChannel().flatMap(channel -> channel.sendMessage(builder.build())).queue();
    }

    private MessageCreateBuilder createMessage(Message message) throws ExecutionException, InterruptedException {
        MessageCreateBuilder data = new MessageCreateBuilder();
        data.setContent(String.format("**%s:**%s%s", message.getAuthor().getName(), "\n", message.getContentDisplay()));

        // TODO split into another message, but I don't anticipate someone sending a giant essay yet
        if (data.getContent().length() > 2000) {
            data.setContent(data.getContent().substring(0, 1997) + "...");
        }

        // Stuffy: Remove any mentions of users, roles, or @everyone/@here
        data.setContent(data.getContent().replaceAll("@(everyone|here|&\\d+)", "@\u200b$1"));

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
