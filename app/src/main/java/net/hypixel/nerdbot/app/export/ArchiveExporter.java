package net.hypixel.nerdbot.app.export;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.ThreadMember;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.discord.config.EmojiConfig;
import net.hypixel.nerdbot.discord.util.StringUtils;
import net.hypixel.nerdbot.marmalade.io.FileUtils;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exports channels, forums and categories to CSV. A thread that can't be read is skipped and named
 * in the result, rather than ending the whole export.
 */
@Slf4j
public class ArchiveExporter {

    static final List<String> CSV_HEADERS = List.of("Timestamp", "Username", "User ID", "Message ID", "Thread ID", "Thread Name", "Reactions", "Message Content");
    static final List<String> DETAILED_HEADERS = List.of("Username", "Thread", "Content", "Agrees", "Disagrees", "Messages");
    // What the archive puts in the thread columns for a message sent in the channel itself
    static final String NO_THREAD = "\\N";
    // Pausing now and then leaves room in the rate limit for everything else the bot is doing
    private static final int PAUSE_EVERY_MESSAGES = 500;
    private static final long PAUSE_MS = 250;

    /** The exported file, and the threads that couldn't be read and so aren't in it. */
    public record Export(ExportFile file, List<String> skippedThreads) {
    }

    private final DiscordUserStore users;
    private final Provider<NerdBotConfig> config;
    private final Clock clock;

    @Inject
    public ArchiveExporter(DiscordUserStore users, Provider<NerdBotConfig> config, Clock clock) {
        this.users = users;
        this.config = config;
        this.clock = clock;
    }

    /**
     * One CSV per text and forum channel in the category, zipped. A channel that fails is left out.
     *
     * @throws IOException when no channel could be exported
     */
    public Export exportCategory(Category category, Consumer<String> progress) throws IOException {
        List<ExportFile> files = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int total = category.getTextChannels().size() + category.getForumChannels().size();
        int done = 0;

        List<ExportStep> steps = new ArrayList<>();
        category.getTextChannels().forEach(channel -> steps.add(new ExportStep(channel.getName(), () -> exportTextChannel(channel, progress))));
        category.getForumChannels().forEach(forum -> steps.add(new ExportStep(forum.getName(), () -> exportForumChannel(forum, progress))));

        for (ExportStep step : steps) {
            try {
                Export export = step.export().get();
                files.add(export.file());
                skipped.addAll(export.skippedThreads());
            } catch (RuntimeException exception) {
                log.error("Failed to archive channel '{}' in category '{}'; leaving it out", step.channelName(), category.getName(), exception);
                skipped.add("#" + step.channelName());
            }
            progress.accept(String.format("Archived %d/%d channels in the category...", ++done, total));
        }

        if (files.isEmpty()) {
            throw new IOException("No channels in category '" + category.getName() + "' could be archived");
        }

        return new Export(ExportFile.of("archive-category-" + category.getName(), "zip", clock.instant(), zip(files)), skipped);
    }

    private record ExportStep(String channelName, Supplier<Export> export) {
    }

    public Export exportTextChannel(TextChannel channel, Consumer<String> progress) {
        CsvTable table = new CsvTable(CSV_HEADERS, ",");
        AtomicInteger counted = new AtomicInteger();
        String context = "Archiving channel " + channel.getName();

        channel.getIterableHistory().stream().forEach(message -> {
            table.addRow(buildRow(message, NO_THREAD, NO_THREAD));
            counted(counted, progress, context);
        });

        List<ThreadChannel> threads = Stream.concat(channel.getThreadChannels().stream(), channel.retrieveArchivedPublicThreadChannels().stream())
            .distinct()
            .toList();
        List<String> skipped = addThreads(table, threads, counted, progress);

        log.info("Archived {} messages from channel '{}' ({}), skipping {} threads", counted.get(), channel.getName(), channel.getId(), skipped.size());
        return new Export(ExportFile.of("archive-" + channel.getName() + "-" + channel.getId(), "csv", clock.instant(), table.toCsv()), skipped);
    }

    public Export exportForumChannel(ForumChannel forum, Consumer<String> progress) {
        CsvTable table = new CsvTable(CSV_HEADERS, ",");
        AtomicInteger counted = new AtomicInteger();

        List<ThreadChannel> threads = allThreads(forum);
        List<String> skipped = addThreads(table, threads, counted, progress);

        log.info("Archived {} messages from forum '{}' ({}), skipping {} threads", counted.get(), forum.getName(), forum.getId(), skipped.size());
        return new Export(ExportFile.of("archive-forum-" + forum.getName() + "-" + forum.getId(), "csv", clock.instant(), table.toCsv()), skipped);
    }

    private static List<ThreadChannel> allThreads(ForumChannel forum) {
        return Stream.concat(forum.getThreadChannels().stream(), forum.retrieveArchivedPublicThreadChannels().stream())
            .distinct()
            .sorted(Comparator.comparing(ThreadChannel::getTimeCreated))
            .toList();
    }

    private List<String> addThreads(CsvTable table, List<ThreadChannel> threads, AtomicInteger counted, Consumer<String> progress) {
        List<String> skipped = new ArrayList<>();

        for (ThreadChannel thread : threads) {
            // Rows are only added once the whole thread is read, so a half-read thread leaves nothing behind
            List<List<String>> rows = new ArrayList<>();
            try {
                thread.getIterableHistory().stream().forEach(message -> {
                    rows.add(buildRow(message, thread.getId(), thread.getName()));
                    counted(counted, progress, "Archiving thread " + thread.getName());
                });
            } catch (RuntimeException exception) {
                log.error("Failed to read thread '{}' ({}); leaving it out", thread.getName(), thread.getId(), exception);
                skipped.add(thread.getName());
                continue;
            }
            rows.forEach(table::addRow);
        }

        return skipped;
    }

    /**
     * One row per suggestion with its votes and every reply, for reviewing a forum in a spreadsheet.
     *
     * @throws IOException when no thread has a first message to export
     */
    public Export exportForumThreadsDetailed(ForumChannel forum, Consumer<String> progress) throws IOException {
        CsvTable table = new CsvTable(DETAILED_HEADERS, ";");
        EmojiConfig emojis = config.get().getEmojiConfig();
        List<ThreadChannel> threads = allThreads(forum);
        List<String> skipped = new ArrayList<>();

        for (int index = 0; index < threads.size(); index++) {
            ThreadChannel thread = threads.get(index);
            progress.accept(String.format("Exporting thread %d/%d: %s", index + 1, threads.size(), thread.getName()));
            try {
                startMessage(thread)
                    .filter(start -> !start.getContentRaw().isBlank())
                    .ifPresent(start -> table.addRow(detailedRow(thread, start, ownerName(thread), replies(thread, start), emojis)));
            } catch (RuntimeException exception) {
                log.error("Failed to export thread '{}' ({}); leaving it out", thread.getName(), thread.getId(), exception);
                skipped.add(thread.getName());
            }
        }

        if (table.isEmpty()) {
            throw new IOException("No threads in forum '" + forum.getName() + "' could be exported");
        }

        return new Export(ExportFile.of("export-threads-" + forum.getName(), "csv", clock.instant(), table.toCsv()), skipped);
    }

    // Empty when the first message was deleted
    private static Optional<Message> startMessage(ThreadChannel thread) {
        try {
            return Optional.ofNullable(thread.retrieveStartMessage().complete());
        } catch (ErrorResponseException exception) {
            if (exception.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private static List<Message> replies(ThreadChannel thread, Message start) {
        return thread.getIterableHistory().stream()
            .filter(message -> !message.getId().equals(start.getId()))
            .sorted(Comparator.comparing(Message::getTimeCreated))
            .toList();
    }

    /** Their linked Minecraft name, else their name in the guild, else their id. */
    String ownerName(ThreadChannel thread) {
        try {
            Optional<DiscordUser> owner = users.findById(thread.getOwnerId());
            if (owner.isPresent() && owner.get().isProfileAssigned()) {
                return owner.get().getMojangProfile().getUsername();
            }

            ThreadMember member = thread.getOwnerThreadMember() != null
                ? thread.getOwnerThreadMember()
                : thread.retrieveThreadMemberById(thread.getOwnerId()).completeAfter(3, TimeUnit.SECONDS);
            return member.getMember().getEffectiveName();
        } catch (RuntimeException exception) {
            log.warn("Couldn't find the name of thread owner {}; using their id", thread.getOwnerId(), exception);
            return thread.getOwnerId();
        }
    }

    static List<String> detailedRow(ThreadChannel thread, Message start, String ownerName, List<Message> replies, EmojiConfig emojis) {
        MessageExport startExport = MessageExport.from(start, true);
        // The title sits in a spreadsheet string literal, where a quote is written twice
        String link = "=HYPERLINK(\"" + start.getJumpUrl() + "\", \"" + thread.getName().replace("\"", "\"\"") + "\")";
        String history = replies.stream().map(reply -> MessageExport.from(reply, true).line()).collect(Collectors.joining("\r\n"));

        return List.of(
            ownerName,
            link,
            start.getContentRaw() + startExport.attachmentSuffix(),
            String.valueOf(countReactions(start, emojis.getAgreeEmojiId())),
            String.valueOf(countReactions(start, emojis.getDisagreeEmojiId())),
            history);
    }

    private static int countReactions(Message message, String emojiId) {
        return message.getReactions().stream()
            .filter(reaction -> reaction.getEmoji().getType() == Emoji.Type.CUSTOM && reaction.getEmoji().asCustom().getId().equals(emojiId))
            .mapToInt(MessageReaction::getCount)
            .findFirst()
            .orElse(0);
    }

    static List<String> buildRow(Message message, String threadId, String threadName) {
        String content = MessageExport.from(message, false).contentWithAttachments();
        if (message.getContentRaw().isEmpty() && !message.getEmbeds().isEmpty()) {
            int embeds = message.getEmbeds().size();
            content += (content.isEmpty() ? "" : "\n") + "Contains " + embeds + " embed" + (embeds == 1 ? "" : "s");
        }

        String reactions = message.getReactions().stream()
            .map(reaction -> reaction.getEmoji().getName() + ":" + reaction.getCount())
            .collect(Collectors.joining(", "));

        return List.of(
            message.getTimeCreated().format(FileUtils.REGULAR_DATE_FORMAT),
            message.getAuthor().getName(),
            message.getAuthor().getId(),
            message.getId(),
            threadId,
            threadName,
            reactions,
            content);
    }

    private static byte[] zip(List<ExportFile> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (ExportFile file : files) {
                zip.putNextEntry(new ZipEntry(file.name()));
                zip.write(file.data());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static void counted(AtomicInteger counter, Consumer<String> progress, String context) {
        int count = counter.incrementAndGet();
        int every = count < 1_000 ? 100 : (int) Math.pow(10, String.valueOf(count).length() - 1);
        if (count % every == 0) {
            progress.accept(context + " - processed " + StringUtils.formatWithCommas(count) + " messages so far!");
        }

        if (count % PAUSE_EVERY_MESSAGES == 0) {
            try {
                Thread.sleep(PAUSE_MS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
