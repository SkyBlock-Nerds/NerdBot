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
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.bot.config.ModMailConfig;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Log4j2
public class ModMailListener {

    public static final String MOD_MAIL_TITLE_TEMPLATE = "%s (%s)";

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
        List<ThreadChannel> channels = new ArrayList<>(modMailChannel.getThreadChannels());
        channels.addAll(modMailChannel.retrieveArchivedPublicThreadChannels().stream().toList());
        Optional<ThreadChannel> existingTicket = channels.stream()
            .filter(threadChannel -> threadChannel.getName().contains(author.getId())) // Find Existing ModMail Thread
            .findFirst();
        boolean updateFirstPost = false;
        String expectedThreadName = MOD_MAIL_TITLE_TEMPLATE.formatted(Util.getDisplayName(author), author.getId());
        ThreadChannel modMailThread;
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(author.getId());
        boolean unlinked = discordUser.noProfileAssigned();
        String username = unlinked ? "**Unlinked**" : discordUser.getMojangProfile().getUsername();
        String uniqueId = unlinked ? "**Unlinked**" : discordUser.getMojangProfile().getUniqueId().toString();
        String expectedFirstPost = "Received new Mod Mail request from " + author.getAsMention() + "!\n\n" +
            "User ID: " + author.getId() + "\n" +
            "Minecraft IGN: " + username + "\n" +
            "Minecraft UUID: " + uniqueId;

        if (existingTicket.isPresent()) {
            modMailThread = existingTicket.get();

            if (modMailThread.isArchived()) {
                modMailThread.getManager().setArchived(false).complete();
                updateFirstPost = true;
            }

            if (!modMailThread.getName().equals(expectedThreadName)) {
                modMailThread.getManager().setName(expectedThreadName).queue();
                updateFirstPost = true;
            }
        } else {
            modMailThread = modMailChannel.createForumPost(expectedThreadName, MessageCreateData.fromContent(expectedFirstPost)).complete().getThreadChannel();
            modMailThread.getManager().setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS).queue();
            String modMailRoleId = NerdBotApp.getBot().getConfig().getModMailConfig().getRoleId();

            if (modMailRoleId != null) {
                modMailThread.getGuild().getMembersWithRoles(RoleManager.getRoleById(modMailRoleId)).forEach(member -> modMailThread.addThreadMember(member).complete());
            }
        }

        if (updateFirstPost) {
            MessageHistory messageHistory = modMailThread.getHistoryFromBeginning(1).complete();
            boolean firstPost = messageHistory.getRetrievedHistory().get(0).getIdLong() == modMailThread.getIdLong();

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

            author.openPrivateChannel()
                .flatMap(channel -> channel.sendMessage(
                    new MessageCreateBuilder().setContent("Thank you for contacting Mod Mail, we will get back with your request shortly.").build()
                ))
                .queue();

            if (unlinked) {
                author.openPrivateChannel()
                    .flatMap(channel -> channel.sendMessage(
                        new MessageCreateBuilder().setContent("You are not linked to Hypixel in SkyBlock Nerds. Please do so using </link:1142633400537186409>.").build()
                    ))
                    .queue();
            }
        }

        Optional<Webhook> webhook = getWebhook();
        log.info(author.getName() + " replied to their Mod Mail request (Thread ID: " + modMailThread.getId() + ")");
        boolean shouldSendMention = shouldAppendRoleMention(discordUser);

        if (webhook.isPresent()) {
            try (JDAWebhookClient client = JDAWebhookClient.from(webhook.get()).onThread(modMailThread.getIdLong())) {
                List<String> messages = buildContent(message, true);

                for (int i = 0; i < messages.size(); i++) {
                    WebhookMessageBuilder webhookMessage = new WebhookMessageBuilder();
                    webhookMessage.setUsername(Util.getDisplayName(event.getAuthor()));
                    webhookMessage.setAvatarUrl(event.getAuthor().getEffectiveAvatarUrl());
                    String content = messages.get(i);

                    if (shouldSendMention) {
                        content = appendModMailRoleMention(messages, content, i);
                    }

                    // Last message
                    if (i == messages.size() - 1) {
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

                // Last message
                if (i == messages.size() - 1) {
                    messageBuilder.setFiles(buildFiles(message));
                }

                messageBuilder.setContent(content);
                modMailThread.sendMessage(messageBuilder.build()).complete();
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
        ModMailConfig.RoleFormat roleFormat = NerdBotApp.getBot().getConfig().getModMailConfig().getRoleFormat();

        if (modMailRoleId == null) {
            return content;
        }

        if (index == 0) { // First message
            if (roleFormat == ModMailConfig.RoleFormat.ABOVE) {
                content = modMailRoleMention + "\n\n" + content;
            } else if (roleFormat == ModMailConfig.RoleFormat.INLINE) {
                content = modMailRoleMention + " " + content;
            }
        }

        if (index == messages.size() - 1 && roleFormat == ModMailConfig.RoleFormat.BELOW) { // Last message
            content += "\n\n" + modMailRoleMention;
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

        return Util.splitString(content, 2_000);
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
