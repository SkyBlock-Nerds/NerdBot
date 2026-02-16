package net.hypixel.nerdbot.app.command;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.ThreadMember;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.hypixel.nerdbot.app.command.util.MessageExport;
import net.hypixel.nerdbot.marmalade.collections.ArrayUtils;
import net.hypixel.nerdbot.marmalade.io.FileUtils;
import net.hypixel.nerdbot.marmalade.csv.CSVData;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;
import net.hypixel.nerdbot.discord.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@UtilityClass
public class ArchiveExporter {

    public static File exportCategory(Category category, Consumer<String> progressCallback) throws IOException {
        List<File> exports = new ArrayList<>();
        List<TextChannel> textChannels = category.getTextChannels();
        List<ForumChannel> forumChannels = category.getForumChannels();
        int totalParents = textChannels.size() + forumChannels.size();
        AtomicInteger processedParents = new AtomicInteger(0);

        log.info("Starting category archive for '{}' (ID: {}) - {} text channels, {} forum channels",
            category.getName(), category.getId(), textChannels.size(), forumChannels.size());

        for (TextChannel textChannel : textChannels) {
            try {
                log.info("Starting archive of text channel '{}' (ID: {})", textChannel.getName(), textChannel.getId());
                exports.add(exportTextChannel(textChannel, progressCallback));
                log.info("Finished archiving text channel '{}' (ID: {})", textChannel.getName(), textChannel.getId());
            } catch (Exception exception) {
                log.error("Failed to archive text channel '{}' (ID: {}), skipping", textChannel.getName(), textChannel.getId(), exception);
            }
            int current = processedParents.incrementAndGet();
            update(progressCallback, String.format("Archived %d/%d parent channels (processing threads)...", current, totalParents));
        }

        for (ForumChannel forumChannel : forumChannels) {
            try {
                log.info("Starting archive of forum channel '{}' (ID: {})", forumChannel.getName(), forumChannel.getId());
                exports.add(exportForumChannel(forumChannel, progressCallback));
                log.info("Finished archiving forum channel '{}' (ID: {})", forumChannel.getName(), forumChannel.getId());
            } catch (Exception exception) {
                log.error("Failed to archive forum channel '{}' (ID: {}), skipping", forumChannel.getName(), forumChannel.getId(), exception);
            }
            int current = processedParents.incrementAndGet();
            update(progressCallback, String.format("Archived %d/%d parent channels (processing threads)...", current, totalParents));
        }

        if (exports.isEmpty()) {
            throw new IOException("No channels found to archive in that category.");
        }

        log.info("All channels processed, creating zip file with {} exports", exports.size());
        return zipFiles(exports, "archive-category-%s-%s".formatted(category.getName(), FileUtils.FILE_NAME_DATE_FORMAT.format(Instant.now())));
    }

    public static File exportTextChannel(TextChannel channel, Consumer<String> progressCallback) throws IOException {
        CSVData csvData = new CSVData(List.of("Timestamp", "Username", "User ID", "Message ID", "Thread ID", "Thread Name", "Reactions", "Message Content"));
        AtomicInteger total = new AtomicInteger(0);

        log.info("[TextChannel '{}' (ID: {})] Starting channel message retrieval", channel.getName(), channel.getId());
        addChannelMessages(csvData, channel, total, progressCallback);
        log.info("[TextChannel '{}' (ID: {})] Finished channel messages, total so far: {}", channel.getName(), channel.getId(), total.get());

        List<ThreadChannel> activeThreads = channel.getThreadChannels();
        log.info("[TextChannel '{}' (ID: {})] Found {} active threads", channel.getName(), channel.getId(), activeThreads.size());
        addThreadMessages(csvData, activeThreads, total, progressCallback);
        log.info("[TextChannel '{}' (ID: {})] Finished active threads, total so far: {}", channel.getName(), channel.getId(), total.get());

        log.info("[TextChannel '{}' (ID: {})] Retrieving archived public threads...", channel.getName(), channel.getId());
        List<ThreadChannel> archivedThreads = channel.retrieveArchivedPublicThreadChannels().stream().toList();
        log.info("[TextChannel '{}' (ID: {})] Found {} archived threads", channel.getName(), channel.getId(), archivedThreads.size());
        addThreadMessages(csvData, archivedThreads, total, progressCallback);
        log.info("[TextChannel '{}' (ID: {})] Finished archived threads, total messages: {}", channel.getName(), channel.getId(), total.get());

        return FileUtils.createTempFile(String.format("archive-%s-%s-%s.csv", channel.getName(), channel.getId(), FileUtils.FILE_NAME_DATE_FORMAT.format(Instant.now())), csvData.toCSV());
    }

    public static File exportForumChannel(ForumChannel forumChannel, Consumer<String> progressCallback) throws IOException {
        CSVData csvData = new CSVData(List.of("Timestamp", "Username", "User ID", "Message ID", "Thread ID", "Thread Name", "Reactions", "Message Content"));
        AtomicInteger total = new AtomicInteger(0);

        List<ThreadChannel> activeThreads = forumChannel.getThreadChannels();
        log.info("[ForumChannel '{}' (ID: {})] Found {} active threads", forumChannel.getName(), forumChannel.getId(), activeThreads.size());
        addThreadMessages(csvData, activeThreads, total, progressCallback);
        log.info("[ForumChannel '{}' (ID: {})] Finished active threads, total so far: {}", forumChannel.getName(), forumChannel.getId(), total.get());

        log.info("[ForumChannel '{}' (ID: {})] Retrieving archived public threads...", forumChannel.getName(), forumChannel.getId());
        List<ThreadChannel> archivedThreads = forumChannel.retrieveArchivedPublicThreadChannels().stream().toList();
        log.info("[ForumChannel '{}' (ID: {})] Found {} archived threads", forumChannel.getName(), forumChannel.getId(), archivedThreads.size());
        addThreadMessages(csvData, archivedThreads, total, progressCallback);
        log.info("[ForumChannel '{}' (ID: {})] Finished archived threads, total messages: {}", forumChannel.getName(), forumChannel.getId(), total.get());

        return FileUtils.createTempFile(String.format("archive-forum-%s-%s-%s.csv", forumChannel.getName(), forumChannel.getId(), FileUtils.FILE_NAME_DATE_FORMAT.format(Instant.now())), csvData.toCSV());
    }

    public static File exportForumThreadsDetailed(ForumChannel forumChannel, Consumer<String> progressCallback) throws IOException {
        CSVData csvData = new CSVData(List.of("Username", "Thread", "Content", "Agrees", "Disagrees", "Messages"), ";");
        List<ThreadChannel> threads = ArrayUtils.safeArrayStream(forumChannel.getThreadChannels().toArray(), forumChannel.retrieveArchivedPublicThreadChannels().stream().toArray())
            .map(ThreadChannel.class::cast)
            .distinct()
            .sorted((o1, o2) -> (int) (o1.getTimeCreated().toEpochSecond() - o2.getTimeCreated().toEpochSecond()))
            .filter(threadChannel -> {
                try {
                    Message startMessage = threadChannel.retrieveStartMessage().complete();
                    return startMessage != null && !startMessage.getContentRaw().isBlank();
                } catch (ErrorResponseException exception) {
                    return exception.getErrorResponse() != net.dv8tion.jda.api.requests.ErrorResponse.UNKNOWN_MESSAGE;
                }
            })
            .toList();
        AtomicInteger index = new AtomicInteger(0);
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        for (ThreadChannel threadChannel : threads) {
            Message startMessage = threadChannel.retrieveStartMessage().complete();
            if (startMessage == null || startMessage.getContentRaw().isBlank()) {
                continue;
            }

            update(progressCallback, String.format("Exporting thread %d/%d: %s", index.get() + 1, threads.size(), threadChannel.getName()));

            DiscordUser discordUser = discordUserRepository.findById(threadChannel.getOwnerId());
            String username;

            try {
                if (discordUser != null && discordUser.isProfileAssigned()) {
                    username = discordUser.getMojangProfile().getUsername();
                } else {
                    ThreadMember threadOwner = threadChannel.getOwnerThreadMember() == null
                        ? threadChannel.retrieveThreadMemberById(threadChannel.getOwnerId()).completeAfter(3, TimeUnit.SECONDS)
                        : threadChannel.getOwnerThreadMember();
                    username = threadOwner.getMember().getEffectiveName();
                }
            } catch (Exception exception) {
                username = threadChannel.getOwnerId();
                log.error("Failed to get username for thread owner " + threadChannel.getOwnerId(), exception);
            }

            String threadUrl = startMessage.getJumpUrl();
            String threadNameEscaped = threadChannel.getName().replace("\"", "\"\"");
            String threadHyperlink = "=HYPERLINK(\"" + threadUrl + "\", \"" + threadNameEscaped + "\")";

            int agrees = 0;
            int disagrees = 0;

            List<MessageReaction> reactions = startMessage.getReactions();
            if (!reactions.isEmpty()) {
                agrees = reactions.stream()
                    .filter(reaction -> reaction.getEmoji().getName().equalsIgnoreCase("agree"))
                    .mapToInt(MessageReaction::getCount)
                    .findFirst()
                    .orElse(0);

                disagrees = reactions.stream()
                    .filter(reaction -> reaction.getEmoji().getName().equalsIgnoreCase("disagree"))
                    .mapToInt(MessageReaction::getCount)
                    .findFirst()
                    .orElse(0);
            }

            List<Message> messages = new ArrayList<>();
            threadChannel.getIterableHistory().forEachRemaining(messages::add);
            messages.sort(java.util.Comparator.comparing(Message::getTimeCreated));
            messages.removeIf(message -> message.getId().equals(startMessage.getId()));

            MessageExport startExport = MessageExport.from(startMessage, true);

            List<String> orderedMessages = new ArrayList<>();
            orderedMessages.add(startExport.line());
            messages.forEach(message -> orderedMessages.add(MessageExport.from(message, true).line()));

            String messageHistory = orderedMessages.size() > 1
                ? String.join("\r\n", orderedMessages.subList(1, orderedMessages.size()))
                : "";

            csvData.addRow(List.of(
                username,
                threadHyperlink,
                "\"" + (startMessage.getContentRaw() + startExport.attachmentSuffix()).replace("\"", "\"\"") + "\"",
                String.valueOf(agrees),
                String.valueOf(disagrees),
                "\"" + messageHistory.replace("\"", "\"\"") + "\""
            ));

            index.incrementAndGet();
            update(progressCallback, String.format("Finished exporting thread %d/%d: %s", index.get(), threads.size(), threadChannel.getName()));
        }

        if (!csvData.hasContent()) {
            throw new IOException("No threads found to export.");
        }

        return FileUtils.createTempFile("export-threads-" + forumChannel.getName() + "-" + FileUtils.FILE_NAME_DATE_FORMAT.format(Instant.now()) + ".csv", csvData.toCSV());
    }

    private static void addChannelMessages(CSVData csvData, TextChannel channel, AtomicInteger counter, Consumer<String> progressCallback) {
        log.info("[addChannelMessages] Starting message iteration for channel '{}' (ID: {})", channel.getName(), channel.getId());
        int channelMessageCount = 0;
        try {
            for (Message message : channel.getIterableHistory()) {
                csvData.addRow(buildRow(message, "\\N", "\\N"));
                channelMessageCount++;
                logProgress(counter, progressCallback, "Archiving channel " + channel.getName() + " (ID: " + channel.getId() + ")");
                throttle(counter);
            }
        } catch (Exception exception) {
            log.error("[addChannelMessages] Error iterating messages for channel '{}' (ID: {}) after {} messages",
                channel.getName(), channel.getId(), channelMessageCount, exception);
            throw exception;
        }
        log.info("[addChannelMessages] Completed channel '{}' (ID: {}) - {} messages archived", channel.getName(), channel.getId(), channelMessageCount);
    }

    private static void addThreadMessages(CSVData csvData, List<ThreadChannel> threads, AtomicInteger counter, Consumer<String> progressCallback) {
        log.info("[addThreadMessages] Processing {} threads", threads.size());
        int threadIndex = 0;
        for (ThreadChannel threadChannel : threads) {
            threadIndex++;
            log.info("[addThreadMessages] [{}/{}] Starting thread '{}' (ID: {})", threadIndex, threads.size(), threadChannel.getName(), threadChannel.getId());
            int threadMessageCount = 0;
            try {
                for (Message message : threadChannel.getIterableHistory()) {
                    csvData.addRow(buildRow(message, threadChannel.getId(), threadChannel.getName()));
                    threadMessageCount++;
                    logProgress(counter, progressCallback, "Archiving thread " + threadChannel.getName() + " (ID: " + threadChannel.getId() + ")");
                    throttle(counter);
                }
            } catch (Exception exception) {
                log.error("[addThreadMessages] Error iterating messages for thread '{}' (ID: {}) after {} messages",
                    threadChannel.getName(), threadChannel.getId(), threadMessageCount, exception);
                throw exception;
            }
            log.info("[addThreadMessages] [{}/{}] Completed thread '{}' (ID: {}) - {} messages", threadIndex, threads.size(), threadChannel.getName(), threadChannel.getId(), threadMessageCount);
        }
        log.info("[addThreadMessages] Finished processing all {} threads", threads.size());
    }

    private static List<String> buildRow(Message message, String threadId, String threadName) {
        String formattedTimestamp = message.getTimeCreated().format(FileUtils.REGULAR_DATE_FORMAT);
        String authorName = message.getAuthor().getName();
        String authorId = message.getAuthor().getName();
        MessageExport export = MessageExport.from(message, false);
        String messageContent = export.contentWithAttachments().replace("\"", "\"\"");
        String reactions = formatReactions(message);

        if (message.getContentRaw().isEmpty() && !message.getEmbeds().isEmpty()) {
            if (!messageContent.isEmpty()) {
                messageContent += "\n";
            }
            messageContent += "Contains " + message.getEmbeds().size() + " embed" + (message.getEmbeds().size() == 1 ? "" : "s");
        }

        return List.of(
            formattedTimestamp,
            authorName,
            authorId,
            message.getId(),
            threadId,
            threadName,
            reactions,
            "\"" + messageContent + "\""
        );
    }

    private static String formatReactions(Message message) {
        if (message.getReactions().isEmpty()) {
            return "";
        }

        return message.getReactions().stream()
            .map(reaction -> String.format("%s:%d", reaction.getEmoji().getName(), reaction.getCount()))
            .collect(Collectors.joining(", "));
    }

    private static File zipFiles(List<File> files, String zipPrefix) throws IOException {
        File zipFile = Files.createTempFile(zipPrefix, ".zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile.toPath()))) {
            for (File file : files) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    ZipEntry zipEntry = new ZipEntry(file.getName());
                    zos.putNextEntry(zipEntry);
                    fis.transferTo(zos);
                    zos.closeEntry();
                }
            }
        }
        return zipFile;
    }

    private static void logProgress(AtomicInteger counter, Consumer<String> progressCallback, String context) {
        int current = counter.incrementAndGet();
        if (current % (current < 1000 ? 100 : (int) Math.pow(10, String.valueOf(current).length() - 1)) == 0) {
            String message = context + " - processed " + StringUtils.COMMA_SEPARATED_FORMAT.format(current) + " message" + (current == 1 ? "" : "s") + " so far!";
            update(progressCallback, message);
        }
    }

    private static void throttle(AtomicInteger counter) {
        int current = counter.get();
        if (current > 0 && current % 500 == 0 && inSafeThread()) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static boolean inSafeThread() {
        String threadName = Thread.currentThread().getName().toLowerCase();
        return !(threadName.contains("mainws") || threadName.equals("main"));
    }

    private static void update(Consumer<String> progressCallback, String status) {
        if (progressCallback != null) {
            progressCallback.accept(status);
        }
    }
}
