package net.hypixel.nerdbot.modmail;

import club.minnced.discord.webhook.external.JDAWebhookClient;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.bot.config.channel.ModMailConfig;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.Util;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

@Log4j2
public class ModMailService {

    public static final String TITLE_TEMPLATE = "%s (%s)";

    private static ModMailService instance;

    private final ModMailConfig config;
    private final DiscordUserRepository userRepository;

    private ModMailService(ModMailConfig config, DiscordUserRepository userRepository) {
        this.config = config;
        this.userRepository = userRepository;
    }

    public static ModMailService getInstance() {
        if (instance == null) {
            synchronized (ModMailService.class) {
                if (instance == null) {
                    ModMailConfig config = NerdBotApp.getBot().getConfig().getModMailConfig();
                    DiscordUserRepository userRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
                    instance = new ModMailService(config, userRepository);
                }
            }
        }
        return instance;
    }

    /**
     * Retrieves the Mod Mail channel from the {@link ChannelCache}.
     *
     * @return An {@link Optional} containing the {@link ForumChannel} if it exists, otherwise empty.
     */
    public Optional<ForumChannel> getModMailChannel() {
        return ChannelCache.getModMailChannel();
    }

    /**
     * Finds an existing Mod Mail thread for a given user.
     *
     * @param user The {@link User} that the thread is associated with.
     *
     * @return An {@link Optional} containing the {@link ThreadChannel} if it exists, otherwise empty.
     */
    public Optional<ThreadChannel> findExistingThread(User user) {
        Optional<ForumChannel> optionalModMailChannel = getModMailChannel();
        if (optionalModMailChannel.isEmpty()) {
            return Optional.empty();
        }

        ForumChannel modMailChannel = optionalModMailChannel.get();
        String expectedThreadName = generateThreadName(user);

        Stream<ThreadChannel> archivedThreads = modMailChannel.retrieveArchivedPublicThreadChannels().stream();
        Optional<ThreadChannel> foundArchivedThread = archivedThreads
            .filter(channel -> channel.getName().equalsIgnoreCase(expectedThreadName))
            .findFirst();

        if (foundArchivedThread.isPresent()) {
            return foundArchivedThread;
        }

        Stream<ThreadChannel> activeThreads = modMailChannel.getThreadChannels().stream();
        return activeThreads
            .filter(thread -> thread.getName().equalsIgnoreCase(expectedThreadName))
            .findFirst();
    }

    /**
     * Creates a new Mod Mail thread for a user.
     *
     * @param user    The {@link User} for whom the thread is being created.
     * @param creator The {@link User} who is creating the thread, or null if it is created by the system.
     *
     * @return The newly created {@link ThreadChannel} for the Mod Mail thread.
     */
    public ThreadChannel createNewThread(User user, User creator) {
        Optional<ForumChannel> optionalModMailChannel = getModMailChannel();
        if (optionalModMailChannel.isEmpty()) {
            throw new IllegalStateException("ModMail channel not found");
        }

        ForumChannel modMailChannel = optionalModMailChannel.get();
        String threadName = generateThreadName(user);
        String initialPost = generateInitialPost(user, creator);

        ThreadChannel thread = modMailChannel
            .createForumPost(threadName, MessageCreateData.fromContent(initialPost))
            .complete()
            .getThreadChannel();

        thread.getManager().setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS).queue();
        addModMailRoleMembersToThread(thread);

        String action = creator != null ? "Forcefully created" : "Created";
        log.info("{} new ModMail thread for {} ({})", action, user.getId(), user.getEffectiveName());

        return thread;
    }

    /**
     * Handles an incoming message from a user in a Mod Mail thread.
     *
     * @param user        The {@link User} who sent the message.
     * @param content     The content of the message sent by the user.
     * @param attachments The list of {@link Message.Attachment} objects attached to the message.
     */
    public void handleIncomingMessage(User user, String content, List<Message.Attachment> attachments) {
        DiscordUser discordUser = userRepository.findOrCreateById(user.getId());

        if (discordUser.noProfileAssigned()) {
            sendUnlinkedUserMessage(user);
            log.info("{} attempted to send mod mail but is not linked to a Minecraft account", user.getName());
            return;
        }

        ThreadChannel thread = getOrCreateThread(user);

        sendUserMessageToThread(thread, user, content, attachments, discordUser);
        sendConfirmationToUser(user);

        discordUser.getLastActivity().setLastModMailUsage(System.currentTimeMillis());
        log.info("{} sent a message to mod mail (Thread ID: {})", user.getName(), thread.getId());
    }

    /**
     * Handles a response from a staff member in a Mod Mail thread.
     *
     * @param staff       The {@link User staff member} who is responding to the Mod Mail thread.
     * @param thread      The {@link ThreadChannel} where the response is being sent.
     * @param content     The content of the response message sent by the staff member.
     * @param attachments The list of {@link Message.Attachment} objects attached to the response message.
     */
    public void handleStaffResponse(User staff, ThreadChannel thread, String content, List<Message.Attachment> attachments) {
        if (content.startsWith("?")) {
            handleHiddenMessage(thread, staff);
            return;
        }

        Optional<User> requester = extractRequesterFromThread(thread);
        if (requester.isEmpty()) {
            log.error("Unable to find requester for thread {}", thread.getId());
            addErrorReaction(thread);
            return;
        }

        sendStaffResponseToUser(requester.get(), staff, content, attachments);
        log.info("{} responded to mod mail thread {} for user {}", staff.getName(), thread.getId(), requester.get().getId());
    }

    /**
     * Checks if a given thread is a Mod Mail thread.
     *
     * @param thread The {@link ThreadChannel} to check.
     *
     * @return True if the thread is a Mod Mail thread, false otherwise.
     */
    public boolean isModMailThread(ThreadChannel thread) {
        if (!(thread.getParentChannel() instanceof ForumChannel)) {
            return false;
        }

        Optional<ForumChannel> modMailChannel = getModMailChannel();
        return modMailChannel.isPresent() && modMailChannel.get().getId().equals(thread.getParentChannel().getId());
    }

    /**
     * Retrieves or creates a Mod Mail thread for a given user.
     *
     * @param user The {@link User} for whom the thread is being retrieved or created.
     *
     * @return The {@link ThreadChannel} representing the Mod Mail thread for the user.
     */
    private ThreadChannel getOrCreateThread(User user) {
        Optional<ThreadChannel> existingThread = findExistingThread(user);

        if (existingThread.isPresent()) {
            ThreadChannel thread = existingThread.get();
            if (thread.isArchived()) {
                thread.getManager().setArchived(false).complete();
                updateFirstPost(thread, user);
            }

            return thread;
        }

        return createNewThread(user, null);
    }

    /**
     * Generates a thread name for a Mod Mail thread based on the user's display name and ID.
     *
     * @param user The {@link User} for whom the thread name is being generated.
     *
     * @return The generated thread name in the format "DisplayName (UserID)".
     */
    private String generateThreadName(User user) {
        return TITLE_TEMPLATE.formatted(Util.getDisplayName(user), user.getId());
    }

    /**
     * Generates the initial post content for a Mod Mail thread.
     *
     * @param user    The {@link User} for whom the initial post is being generated.
     * @param creator The {@link User} who created the thread, or null if it was created by the system.
     *
     * @return The content of the initial post in the Mod Mail thread.
     */
    private String generateInitialPost(User user, User creator) {
        DiscordUser discordUser = userRepository.findById(user.getId());
        if (discordUser == null) {
            discordUser = new DiscordUser(user.getId());
            userRepository.cacheObject(discordUser);
        }

        String username = discordUser.noProfileAssigned() ? "**Unlinked**" : discordUser.getMojangProfile().getUsername();
        String uniqueId = discordUser.noProfileAssigned() ? "**Unlinked**" : discordUser.getMojangProfile().getUniqueId().toString();

        String action = creator != null ? "Created a Mod Mail request from" : "Received new Mod Mail request from";
        return action + " " + user.getAsMention() + "!\n\n" +
            "User ID: " + user.getId() + "\n" +
            "Minecraft IGN: " + username + "\n" +
            "Minecraft UUID: " + uniqueId;
    }

    /**
     * Updates the first post in a Mod Mail thread if it is the initial post.
     *
     * @param thread The {@link ThreadChannel} representing the Mod Mail thread.
     * @param user   The {@link User} who sent the initial message in the thread.
     */
    private void updateFirstPost(ThreadChannel thread, User user) {
        MessageHistory messageHistory = thread.getHistoryFromBeginning(1).complete();
        boolean firstPost = messageHistory.getRetrievedHistory().get(0).getIdLong() == thread.getIdLong();

        if (firstPost) {
            String updatedPost = generateInitialPost(user, null);
            messageHistory.getRetrievedHistory()
                .get(0)
                .editMessage(new MessageEditBuilder().setContent(updatedPost).build())
                .queue();
        }
    }

    /**
     * Adds members with the Mod Mail role to a newly created thread.
     *
     * @param thread The {@link ThreadChannel} to which the Mod Mail role members will be added.
     */
    private void addModMailRoleMembersToThread(ThreadChannel thread) {
        String modMailRoleId = config.getRoleId();
        if (modMailRoleId != null) {
            RoleManager.getRoleById(modMailRoleId).ifPresent(role -> {
                thread.getGuild().getMembersWithRoles(role).forEach(member ->
                    thread.addThreadMember(member).queue(
                        success -> log.info("Successfully added member {} to thread {}", member.getEffectiveName(), thread.getName()),
                        error -> log.error("Failed to add member {} to thread {}", member.getEffectiveName(), thread.getName(), error)
                    )
                );
            });
        }
    }

    /**
     * Extracts the requester from a Mod Mail thread based on the thread name.
     *
     * @param thread The {@link ThreadChannel} from which to extract the requester.
     *
     * @return An {@link Optional} containing the {@link User} who is the requester, or empty if extraction fails.
     */
    private Optional<User> extractRequesterFromThread(ThreadChannel thread) {
        try {
            String threadName = thread.getName();
            String userId = threadName.substring(threadName.lastIndexOf("(") + 1, threadName.lastIndexOf(")"));
            User requester = NerdBotApp.getBot().getJDA().getUserById(userId);

            return Optional.ofNullable(requester);
        } catch (Exception e) {
            log.error("Failed to extract requester from thread name: {}", thread.getName(), e);
            return Optional.empty();
        }
    }

    /**
     * Sends a message from a user to a Mod Mail thread, handling role mentions and attachments.
     *
     * @param thread      The {@link ThreadChannel} where the message will be sent.
     * @param user        The {@link User} who sent the message.
     * @param content     The content of the message sent by the user.
     * @param attachments The list of {@link Message.Attachment} objects attached to the message.
     * @param discordUser The {@link DiscordUser} object representing the user, used to check if the role should be pinged.
     */
    private void sendUserMessageToThread(ThreadChannel thread, User user, String content, List<Message.Attachment> attachments, DiscordUser discordUser) {
        Optional<Webhook> webhook = getWebhook();
        List<String> messageChunks = buildContent(content, webhook.isPresent(), user);
        boolean shouldPingRole = shouldPingRole(discordUser);

        if (webhook.isPresent()) {
            sendViaWebhook(webhook.get(), thread, user, messageChunks, attachments, shouldPingRole);
        } else {
            sendDirectly(thread, messageChunks, attachments, shouldPingRole);
        }
    }

    /**
     * Sends a response from a staff member to the user who owns the Mod Mail thread.
     *
     * @param requester   The {@link User} who owns the Mod Mail thread.
     * @param staff       The {@link User staff member} who is responding to the Mod Mail thread.
     * @param content     The content of the response message sent by the staff member.
     * @param attachments The list of {@link Message.Attachment} objects attached to the response message.
     */
    private void sendStaffResponseToUser(User requester, User staff, String content, List<Message.Attachment> attachments) {
        String formattedContent = "**[Mod Mail] " + Util.getDisplayName(staff) + ":** " + content;
        List<FileUpload> files = buildFileUploads(attachments);

        requester.openPrivateChannel()
            .flatMap(channel -> channel.sendMessage(
                new MessageCreateBuilder()
                    .setContent(formattedContent)
                    .setFiles(files)
                    .build()
            ))
            .queue();
    }

    /**
     * Sends a confirmation message to the user after they have contacted Mod Mail.
     *
     * @param user The {@link User} who contacted Mod Mail.
     */
    private void sendConfirmationToUser(User user) {
        user.openPrivateChannel()
            .flatMap(channel -> channel.sendMessage(
                "Thank you for contacting Mod Mail, we will get back with your request shortly."
            ))
            .queue();
    }

    /**
     * Sends a message to the user if they attempt to use Mod Mail without linking their Minecraft account.
     *
     * @param user The {@link User} who attempted to use Mod Mail.
     */
    private void sendUnlinkedUserMessage(User user) {
        user.openPrivateChannel()
            .flatMap(channel -> channel.sendMessage(
                """                
                You must link your Minecraft account before using Mod Mail.
                Please link your account using </link:" + SLASH_COMMAND_LINK_ID + "> and try again."""
            ))
            .queue();
    }

    /**
     * Handles a hidden message sent by staff in a Mod Mail thread.
     *
     * @param thread The {@link ThreadChannel} where the hidden message was sent.
     * @param staff  The {@link User} who sent the hidden message.
     */
    private void handleHiddenMessage(ThreadChannel thread, User staff) {
        thread.retrieveMessageById(thread.getLatestMessageId())
            .queue(message -> message.addReaction(Emoji.fromUnicode("U+1F92B")).queue());
        log.info("{} sent a hidden message (Thread ID: {})", staff.getName(), thread.getId());
    }

    /**
     * Adds an error reaction to the latest message in a thread when an error occurs.
     * This is used to indicate that something went wrong, such as failing to send the message.
     *
     * @param thread The {@link ThreadChannel} where the error occurred.
     */
    private void addErrorReaction(ThreadChannel thread) {
        thread.retrieveMessageById(thread.getLatestMessageId())
            .queue(message -> message.addReaction(Emoji.fromUnicode("U+274C")).queue());
    }


    /**
     * Determines whether to ping the Mod Mail role based on the last time one was sent.
     *
     * @param discordUser The {@link DiscordUser} object representing the user.
     *
     * @return True if the role should be pinged, false otherwise.
     */
    private boolean shouldPingRole(DiscordUser discordUser) {
        long lastUsage = discordUser.getLastActivity().getLastModMailUsage();
        long currentTime = System.currentTimeMillis();
        int timeBetweenPings = config.getTimeBetweenPings();

        return (currentTime - lastUsage) / 1000 > timeBetweenPings;
    }

    /**
     * Sends a message via a {@link Webhook} to a {@link ThreadChannel}, handling role mentions and attachments.
     *
     * @param webhook        The {@link Webhook} to use for sending the message.
     * @param thread         The {@link ThreadChannel} to which the message will be sent.
     * @param user           The {@link User} who sent the message, used for display name and avatar.
     * @param messageChunks  The list of message chunks to be sent, ensuring they do not exceed Discord's character limit.
     * @param attachments    The list of {@link Message.Attachment} objects to be included in the message.
     * @param shouldPingRole Whether to append a role mention to the message content based on the configured {@link ModMailConfig#timeBetweenPings}
     */
    private void sendViaWebhook(Webhook webhook, ThreadChannel thread, User user, List<String> messageChunks, List<Message.Attachment> attachments, boolean shouldPingRole) {
        try (JDAWebhookClient client = JDAWebhookClient.from(webhook).onThread(thread.getIdLong())) {
            for (int i = 0; i < messageChunks.size(); i++) {
                WebhookMessageBuilder webhookMessage = new WebhookMessageBuilder();
                webhookMessage.setUsername(Util.getDisplayName(user));
                webhookMessage.setAvatarUrl(user.getEffectiveAvatarUrl());

                String content = messageChunks.get(i);
                if (shouldPingRole) {
                    content = appendRoleMention(messageChunks, content, i);
                }

                if (i == messageChunks.size() - 1) {
                    buildFileUploads(attachments).forEach(fileUpload ->
                        webhookMessage.addFile(fileUpload.getName(), fileUpload.getData())
                    );
                }

                webhookMessage.setContent(content);
                client.send(webhookMessage.build());
            }
        }
    }

    /**
     * Sends a message directly to a thread, handling role mentions and attachments.
     *
     * @param thread         The {@link ThreadChannel} to which the message will be sent.
     * @param messageChunks  The list of message chunks to be sent.
     * @param attachments    The list of {@link Message.Attachment} objects to be included in the message.
     * @param shouldPingRole Whether to append a role mention to the message content.
     */
    private void sendDirectly(ThreadChannel thread, List<String> messageChunks, List<Message.Attachment> attachments, boolean shouldPingRole) {
        for (int i = 0; i < messageChunks.size(); i++) {
            MessageCreateBuilder messageBuilder = new MessageCreateBuilder();
            String content = messageChunks.get(i);

            if (shouldPingRole) {
                content = appendRoleMention(messageChunks, content, i);
            }

            if (i == messageChunks.size() - 1) {
                messageBuilder.setFiles(buildFileUploads(attachments));
            }

            messageBuilder.setContent(content);
            thread.sendMessage(messageBuilder.build()).complete();
        }
    }

    /**
     * Builds the content for a message, ensuring it does not exceed Discord's character limit.
     *
     * @param content The original content of the {@link Message}.
     * @param webhook Whether the message is being sent via a {@link Webhook}.
     * @param user    The {@link User} who sent the message.
     *
     * @return A list of strings, each representing a chunk of the message content.
     */
    private List<String> buildContent(String content, boolean webhook, User user) {
        content = content.replaceAll("@(everyone|here|&\\d+)", "@\u200b$1");

        if (!webhook) {
            content = String.format("**%s:**%s%s", Util.getDisplayName(user), "\n", content);
        }

        return Util.splitString(content, MAX_CONTENT_CHUNK_SIZE);
    }

    /**
     * Appends a role mention to the content based on the configured {@link ModMailConfig.RoleFormat}.
     *
     * @param messages The list of messages to check the index against.
     * @param content  The content to which the role mention will be appended.
     * @param index    The index of the current message in the list.
     *
     * @return The content with the role mention appended based on the index and role format.
     */
    private String appendRoleMention(List<String> messages, String content, int index) {
        String modMailRoleId = config.getRoleId();
        if (modMailRoleId == null) {
            return content;
        }

        String roleMention = "<@&" + modMailRoleId + ">";
        ModMailConfig.RoleFormat roleFormat = config.getRoleFormat();

        if (index == 0) {
            if (roleFormat == ModMailConfig.RoleFormat.ABOVE) {
                content = roleMention + "\n\n" + content;
            } else if (roleFormat == ModMailConfig.RoleFormat.INLINE) {
                content = roleMention + " " + content;
            }
        }

        if (index == messages.size() - 1 && roleFormat == ModMailConfig.RoleFormat.BELOW) {
            content += "\n\n" + roleMention;
        }

        return content;
    }

    /**
     * Builds a list of {@link FileUpload} objects from the provided attachments.
     *
     * @param attachments The list of {@link Message.Attachment} objects to convert.
     *
     * @return A list of {@link FileUpload} objects.
     */
    private List<FileUpload> buildFileUploads(List<Message.Attachment> attachments) {
        return attachments.stream()
            .map(attachment -> {
                try {
                    return FileUpload.fromData(
                        attachment.getProxy().download().get(),
                        attachment.getFileName()
                    );
                } catch (InterruptedException | ExecutionException exception) {
                    log.error("Failed to download attachment {}", attachment.getFileName(), exception);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Retrieves the configured webhook for ModMail.
     *
     * @return An {@link Optional} containing the webhook if it exists, otherwise empty.
     */
    private Optional<Webhook> getWebhook() {
        String webhookId = config.getWebhookId();
        if (webhookId == null || webhookId.isEmpty()) {
            return Optional.empty();
        }

        return Util.getMainGuild()
            .retrieveWebhooks()
            .complete()
            .stream()
            .filter(webhook -> webhook.getId().equals(webhookId))
            .findFirst();
    }
}