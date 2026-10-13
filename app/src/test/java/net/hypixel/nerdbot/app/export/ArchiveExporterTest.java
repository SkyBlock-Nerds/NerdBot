package net.hypixel.nerdbot.app.export;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.pagination.MessagePaginationAction;
import net.dv8tion.jda.api.requests.restaction.pagination.ThreadChannelPaginationAction;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveExporterTest {

    private static final Instant NOW = Instant.parse("2026-10-13T09:30:00Z");
    private static final OffsetDateTime SENT = OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final String AGREE = "1";
    private static final String DISAGREE = "2";

    private final NerdBotConfig config = new NerdBotConfig();
    private final FakeDiscordUserStore users = new FakeDiscordUserStore();
    private final ArchiveExporter exporter = new ArchiveExporter(users, () -> config, Clock.fixed(NOW, ZoneOffset.UTC));
    private final List<String> progress = new ArrayList<>();

    @BeforeEach
    void setUp() {
        config.getEmojiConfig().setAgreeEmojiId(AGREE);
        config.getEmojiConfig().setDisagreeEmojiId(DISAGREE);
    }

    private static Message message(String id, String content, MessageReaction... reactions) {
        return Fakes.of(Message.class, Map.of(
            "getId", id,
            "getContentRaw", content,
            "getAuthor", Fakes.of(User.class, Map.of("getName", "author", "getId", "7")),
            "getTimeCreated", SENT.plusMinutes(Long.parseLong(id)),
            "getJumpUrl", "https://discord.com/channels/1/2/" + id,
            "getReactions", List.of(reactions)));
    }

    private static MessageReaction reaction(String emojiId, int count) {
        return new MessageReaction(Fakes.of(JDA.class, Map.of()), Fakes.of(MessageChannel.class, Map.of()),
            (EmojiUnion) Emoji.fromCustom("emoji" + emojiId, Long.parseLong(emojiId), false), 1, 2, new boolean[]{false, false}, new int[]{count, 0});
    }

    private static MessagePaginationAction history(List<Message> messages) {
        return Fakes.of(MessagePaginationAction.class, Map.of("stream", (CallRecorder.Answer) args -> messages.stream()));
    }

    private static MessagePaginationAction brokenHistory() {
        return Fakes.of(MessagePaginationAction.class, Map.of("stream", (CallRecorder.Answer) args -> {
            throw new IllegalStateException("Missing access");
        }));
    }

    private static ThreadChannel thread(String id, String name, MessagePaginationAction history, Message start) {
        return Fakes.of(ThreadChannel.class, Map.of(
            "getId", id, "getName", name, "getOwnerId", "7",
            "getTimeCreated", SENT.plusDays(Long.parseLong(id)),
            "getIterableHistory", history,
            "retrieveStartMessage", Fakes.of(RestAction.class, Map.of("complete", (CallRecorder.Answer) args -> start))));
    }

    private static ThreadChannelPaginationAction noArchivedThreads() {
        return Fakes.of(ThreadChannelPaginationAction.class, Map.of("stream", (CallRecorder.Answer) args -> Stream.empty()));
    }

    private static ForumChannel forum(String name, ThreadChannel... threads) {
        return Fakes.of(ForumChannel.class, Map.of(
            "getId", "50", "getName", name,
            "getThreadChannels", List.of(threads),
            "retrieveArchivedPublicThreadChannels", noArchivedThreads()));
    }

    private static String csv(ArchiveExporter.Export export) {
        return new String(export.file().data(), StandardCharsets.UTF_8);
    }

    @Test
    void aThreadThatCantBeReadIsLeftOutAndNamed() {
        ThreadChannel readable = thread("1", "readable", history(List.of(message("10", "hello"), message("11", "there"))), null);
        ThreadChannel broken = thread("2", "broken", brokenHistory(), null);

        ArchiveExporter.Export export = exporter.exportForumChannel(forum("ideas", readable, broken), progress::add);

        assertEquals(List.of("broken"), export.skippedThreads());
        List<String> lines = csv(export).lines().toList();
        assertEquals(3, lines.size(), "the header and the readable thread's two messages");
        assertTrue(lines.stream().noneMatch(line -> line.contains("broken")));
    }

    @Test
    void valuesWithCommasQuotesAndLineBreaksStayInTheirColumn() {
        ThreadChannel thread = thread("1", "a, b", history(List.of(message("10", "say \"hi\"\nbye"))), null);

        String content = csv(exporter.exportForumChannel(forum("ideas", thread), progress::add));

        assertTrue(content.contains("\"a, b\""), content);
        assertTrue(content.contains("\"say \"\"hi\"\"\nbye\""), content);
    }

    @Test
    void theFileIsNamedAfterTheForumAndTime() {
        ArchiveExporter.Export export = exporter.exportForumChannel(forum("ideas/old"), progress::add);

        assertEquals("archive-forum-ideas_old-50-2026-10-13_09-30-00.csv", export.file().name());
    }

    @Test
    void theDetailedExportHasVotesRepliesAndTheOwnersMinecraftName() throws IOException {
        DiscordUser owner = new DiscordUser("7");
        owner.getMojangProfile().setUniqueId(UUID.randomUUID());
        owner.getMojangProfile().setUsername("Notch");
        users.seed(owner);
        Message start = message("10", "an idea", reaction(AGREE, 5), reaction(DISAGREE, 2));
        ThreadChannel thread = thread("1", "Idea", history(List.of(message("12", "second"), start, message("11", "first"))), start);

        ArchiveExporter.Export export = exporter.exportForumThreadsDetailed(forum("ideas", thread), progress::add);

        List<String> row = ArchiveExporter.detailedRow(thread, start, "Notch", List.of(message("11", "first"), message("12", "second")), config.getEmojiConfig());
        assertEquals(List.of("Notch", "an idea", "5", "2"), List.of(row.get(0), row.get(2), row.get(3), row.get(4)));
        assertTrue(row.get(5).indexOf("first") < row.get(5).indexOf("second"), "replies are oldest first");
        String content = csv(export);
        assertTrue(content.startsWith("Username;Thread;Content;Agrees;Disagrees;Messages"), content);
        assertTrue(content.contains("Notch;"), content);
        assertTrue(export.skippedThreads().isEmpty());
    }

    @Test
    void aThreadWhoseFirstMessageIsBlankIsNotExported() {
        Message blank = message("10", "  ");
        ThreadChannel thread = thread("1", "Empty", history(List.of(blank)), blank);

        assertThrows(IOException.class, () -> exporter.exportForumThreadsDetailed(forum("ideas", thread), progress::add));
    }

    @Test
    void anOwnerWithoutAProfileOrMembershipIsNamedByTheirId() {
        ThreadChannel thread = thread("1", "Idea", history(List.of()), null);

        assertEquals("7", exporter.ownerName(thread));
    }

    @Test
    void aCategoryArchiveZipsEachChannelAndLeavesOutOnesThatFail() throws IOException {
        TextChannel general = Fakes.of(TextChannel.class, Map.of(
            "getId", "60", "getName", "general",
            "getIterableHistory", history(List.of(message("10", "hi"))),
            "retrieveArchivedPublicThreadChannels", noArchivedThreads()));
        ForumChannel broken = Fakes.of(ForumChannel.class, Map.of("getId", "61", "getName", "broken",
            "getThreadChannels", (CallRecorder.Answer) args -> {
                throw new IllegalStateException("Missing access");
            }));
        Category category = Fakes.of(Category.class, Map.of("getId", "5", "getName", "Old Stuff",
            "getTextChannels", List.of(general), "getForumChannels", List.of(broken)));

        ArchiveExporter.Export export = exporter.exportCategory(category, progress::add);

        assertEquals("archive-category-Old_Stuff-2026-10-13_09-30-00.zip", export.file().name());
        assertEquals(List.of("#broken"), export.skippedThreads());
        assertEquals(List.of("archive-general-60-2026-10-13_09-30-00.csv"), zipEntries(export.file().data()));
    }

    @Test
    void aCategoryWithNothingToArchiveFails() {
        Category empty = Fakes.of(Category.class, Map.of("getId", "5", "getName", "Empty"));

        assertThrows(IOException.class, () -> exporter.exportCategory(empty, progress::add));
    }

    @Test
    void progressIsReportedAsChannelsFinish() throws IOException {
        TextChannel general = Fakes.of(TextChannel.class, Map.of(
            "getId", "60", "getName", "general",
            "getIterableHistory", history(List.of()),
            "retrieveArchivedPublicThreadChannels", noArchivedThreads()));
        Category category = Fakes.of(Category.class, Map.of("getId", "5", "getName", "Cat", "getTextChannels", List.of(general)));

        exporter.exportCategory(category, progress::add);

        assertEquals(List.of("Archived 1/1 channels in the category..."), progress);
        assertFalse(progress.isEmpty());
    }

    private static List<String> zipEntries(byte[] zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}
