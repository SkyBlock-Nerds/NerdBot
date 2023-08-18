package net.hypixel.nerdbot.listener;

import club.minnced.discord.webhook.external.JDAWebhookClient;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Log4j2
public class ModMailListener {

    private static final String MOD_MAIL_TITLE_TEMPLATE = "%s (%s)";

    @SubscribeEvent
    public void onModMailReceived(MessageReceivedEvent event) {
        User author = event.getAuthor();
        if (author.isBot() || author.isSystem()) {
            return;
        }

        if (event.getChannelType() != ChannelType.PRIVATE) {
            return;
        }

        ForumChannel modMailChannel = getModMailChannel();
        if (modMailChannel == null) {
            return;
        }

        Message message = event.getMessage();
        Database database = NerdBotApp.getBot().getDatabase();
        List<ThreadChannel> channels = new ArrayList<>(modMailChannel.getThreadChannels());
        channels.addAll(modMailChannel.retrieveArchivedPublicThreadChannels().stream().toList());
        Optional<ThreadChannel> existingTicket = channels.stream()
            .filter(threadChannel -> threadChannel.getName().contains(author.getId())) // Find Existing ModMail Thread
            .findFirst();
        boolean sendThanks = existingTicket.map(ThreadChannel::isArchived).orElse(existingTicket.isEmpty());
        String expectedThreadName = MOD_MAIL_TITLE_TEMPLATE.formatted(Util.getDisplayName(event.getAuthor()), author.getId());
        ThreadChannel threadChannel;
        DiscordUser discordUser = Util.getOrAddUserToCache(database, event.getAuthor().getId());
        String expectedFirstPost = "Received new Mod Mail request from " + author.getAsMention() + "!\n\n" +
            "User ID: " + author.getId() + "\n" +
            "Minecraft IGN: " + discordUser.getMojangProfile().getUsername() + "\n" +
            "Minecraft UUID: " + discordUser.getMojangProfile().getUniqueId().toString();

        if (sendThanks) {
            event.getAuthor()
                .openPrivateChannel()
                .flatMap(channel -> channel.sendMessage(
                    new MessageCreateBuilder().setContent("Thank you for contacting Mod Mail, we will get back with your request shortly.").build()
                ))
                .queue();
        }

        if (existingTicket.isPresent()) {
            threadChannel = existingTicket.get();
            boolean updateFirstPost = false;

            if (threadChannel.isArchived()) {
                threadChannel.getManager().setArchived(false).complete();
                updateFirstPost = true;
            }

            if (!threadChannel.getName().equals(expectedThreadName)) {
                threadChannel.getManager().setName(expectedThreadName).queue();
                updateFirstPost = true;
            }

            if (updateFirstPost) {
                MessageHistory messageHistory = threadChannel.getHistoryFromBeginning(1).complete();
                boolean firstPost = messageHistory.getRetrievedHistory().get(0).getIdLong() == threadChannel.getIdLong();

                if (firstPost) {
                    messageHistory.getRetrievedHistory()
                        .get(0)
                        .editMessage(
                            new MessageEditBuilder()
                                .setContent(expectedFirstPost)
                                .build()
                        )
                        .queue();
                }
            }
        } else {
            threadChannel = modMailChannel.createForumPost(expectedThreadName, MessageCreateData.fromContent(expectedFirstPost)).complete().getThreadChannel();
            threadChannel.getManager().setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS).queue();
            String modMailRoleId = NerdBotApp.getBot().getConfig().getModMailConfig().getRoleId();

            if (modMailRoleId != null) {
                threadChannel.getGuild().getMembersWithRoles(Util.getRoleById(modMailRoleId)).forEach(member -> threadChannel.addThreadMember(member).complete());
            }
        }

        Optional<Webhook> webhook = getWebhook();
        log.info(author.getName() + " replied to their Mod Mail request (Thread ID: " + threadChannel.getId() + ")");
        boolean shouldSendMention = shouldAppendRoleMention(discordUser);

        if (webhook.isPresent()) {
            try (JDAWebhookClient client = JDAWebhookClient.from(webhook.get()).onThread(threadChannel.getIdLong())) {
                List<String> messages = buildContent(message, true);

                for (int i = 0; i < messages.size(); i++) {
                    WebhookMessageBuilder webhookMessage = new WebhookMessageBuilder();
                    webhookMessage.setUsername(Util.getDisplayName(event.getAuthor()));
                    webhookMessage.setAvatarUrl(event.getAuthor().getEffectiveAvatarUrl());
                    String content = messages.get(i);

                    if (shouldSendMention) {
                        content = appendModMailRoleMention(messages, content, i);
                    }

                    if (i == messages.size() - 1) { // Last message
                        buildFiles(message).forEach(fileUpload -> webhookMessage.addFile(fileUpload.getName(), fileUpload.getData()));
                    }

                    webhookMessage.setContent(content);
                    client.send(webhookMessage.build());
                }
            }
        } else {
            List<String> messages = buildContent(message, false);

            for (int i = 0; i < messages.size(); i++) {
                MessageCreateBuilder messageBuilder = new MessageCreateBuilder();
                String content = messages.get(i);

                if (shouldSendMention) {
                    content = appendModMailRoleMention(messages, content, i);
                }

                if (i == messages.size() - 1) { // Last message
                    messageBuilder.setFiles(buildFiles(message));
                }

                messageBuilder.setContent(content);
                threadChannel.sendMessage(messageBuilder.build()).complete();
            }
        }

        // Update last use
        discordUser.getLastActivity().setLastModMailUsage(System.currentTimeMillis());
    }

    private static boolean shouldAppendRoleMention(DiscordUser discordUser) {
        long lastUsage = discordUser.getLastActivity().getLastModMailUsage();
        long currentTime = System.currentTimeMillis();
        int timeBetweenPings = NerdBotApp.getBot().getConfig().getModMailConfig().getTimeBetweenPings();
        return (currentTime - lastUsage) / 1000 > timeBetweenPings;
    }

    private static String appendModMailRoleMention(List<String> messages, String content, int index) {
        String modMailRoleId = NerdBotApp.getBot().getConfig().getModMailConfig().getRoleId();
        String modMailRoleMention = "<@&%s>".formatted(modMailRoleId);
        String roleFormat = NerdBotApp.getBot().getConfig().getModMailConfig().getRoleFormat();

        if (index == 0) { // First message
            if (modMailRoleId != null) {
                if (roleFormat.equalsIgnoreCase("ABOVE")) {
                    content = modMailRoleMention + "\n\n" + content;
                } else if (roleFormat.equalsIgnoreCase("INLINE")) {
                    content = modMailRoleMention + " " + content;
                }
            }
        }

        if (index == messages.size() - 1) { // Last message
            if (modMailRoleId != null) {
                if (roleFormat.equalsIgnoreCase("BELOW")) {
                    content += "\n\n" + modMailRoleMention;
                }
            }
        }

        return content;
    }

    @SubscribeEvent
    public void onModMailResponse(MessageReceivedEvent event) {
        User author = event.getAuthor();
        if (author.isBot() || author.isSystem()) {
            return;
        }

        if (event.getChannelType() != ChannelType.GUILD_PUBLIC_THREAD) {
            return;
        }

        ThreadChannel threadChannel = event.getChannel().asThreadChannel();
        if (!(threadChannel.getParentChannel() instanceof ForumChannel)) {
            return;
        }

        ForumChannel modMailChannel = getModMailChannel();
        if (modMailChannel == null || !modMailChannel.getId().equals(threadChannel.getParentChannel().getId())) {
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

        requester.openPrivateChannel()
            .flatMap(channel -> channel.sendMessage(
                new MessageCreateBuilder()
                    .setContent("**[Mod Mail] " + Util.getDisplayName(author) + ":** " + message.getContentDisplay())
                    .setFiles(buildFiles(message))
                    .build()
            ))
            .queue();
    }

    private static Optional<Webhook> getWebhook() {
        return Util.getMainGuild()
            .retrieveWebhooks()
            .complete()
            .stream()
            .filter(webhook -> webhook.getId().equals(
                NerdBotApp.getBot()
                    .getConfig()
                    .getModMailConfig()
                    .getWebhookId()
            ))
            .findFirst();
    }

    private static ForumChannel getModMailChannel() {
        return NerdBotApp.getBot()
            .getJDA()
            .getForumChannelById(
                NerdBotApp.getBot()
                    .getConfig()
                    .getModMailConfig()
                    .getChannelId()
            );
    }

    private static List<String> buildContent(Message message, boolean webhook) {
        String content = message.getContentDisplay();

        // Remove any mentions of users, roles, or @everyone/@here
        content = content.replaceAll("@(everyone|here|&\\d+)", "@\u200b$1");

        if (!webhook) {
            content = String.format("**%s:**%s%s", Util.getDisplayName(message.getAuthor()), "\n", content);
        }

        List<String> messages = new ArrayList<>();

        if (content.length() > 2000) {
            String subContent = content;

            while (subContent.length() > 1950) {
                subContent = content.substring(0, 1950);
                messages.add(subContent);
            }

            messages.add(subContent);
        } else {
            messages.add(content);
        }

        return messages;
    }

    private static List<FileUpload> buildFiles(Message message) {
        return message.getAttachments()
            .stream()
            .map(attachment -> {
                try {
                    return FileUpload.fromData(
                        attachment.getProxy().download().get(),
                        attachment.getFileName()
                    );
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }
            })
            .collect(Collectors.toList());
    }

}
