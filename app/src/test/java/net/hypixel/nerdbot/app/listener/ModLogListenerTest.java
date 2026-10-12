package net.hypixel.nerdbot.app.listener;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.cache.MessageCache;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.hypixel.nerdbot.app.testsupport.MemberFakes.MAIN_GUILD_ID;
import static net.hypixel.nerdbot.app.testsupport.MemberFakes.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModLogListenerTest {

    private static final String MODERATOR_ROLE = "77";

    private final NerdBotConfig config = new NerdBotConfig();
    private final MessageCache messageCache = new MessageCache();
    private final List<MessageEmbed> logged = new ArrayList<>();
    private final ModLogListener listener = new ModLogListener(new GuildScope(() -> config), logged::add, messageCache, () -> config);
    private boolean moderatorsCanSee = true;
    private GuildMessageChannel channel;

    @BeforeEach
    void setUp() {
        config.setGuildId(MAIN_GUILD_ID);
        config.getRoleConfig().setModeratorRoleId(MODERATOR_ROLE);
        Role moderator = Fakes.of(Role.class, Map.of("getId", MODERATOR_ROLE, "hasAccess", (CallRecorder.Answer) args -> moderatorsCanSee));
        Guild guild = Fakes.of(Guild.class, Map.of("getId", MAIN_GUILD_ID, "getRoles", List.of(moderator)));
        channel = Fakes.of(GuildMessageChannel.class, Map.of("getId", "10", "getAsMention", "<#10>", "getGuild", guild));
    }

    private static Message message(String id, String content) {
        return Fakes.of(Message.class, Map.of(
            "getId", id, "getAuthor", user("1", false), "getContentRaw", content, "getContentDisplay", content));
    }

    private void cache(Message message) {
        messageCache.onMessageReceived(new MessageReceivedEvent(Fakes.of(JDA.class, Map.of()), 0, message));
    }

    private Map<String, String> fields(MessageEmbed embed) {
        Map<String, String> fields = new HashMap<>();
        embed.getFields().forEach(field -> fields.put(field.getName(), field.getValue()));
        return fields;
    }

    @Test
    void aDeletedMessageIsLoggedAsItWas() {
        cache(message("5", "hello there"));

        listener.messageDeleted("5", channel);

        assertEquals(1, logged.size());
        assertEquals("Message deleted", logged.getFirst().getTitle());
        assertEquals("hello there", fields(logged.getFirst()).get("Content"));
        assertEquals("<#10>", fields(logged.getFirst()).get("Channel"));
    }

    @Test
    void longContentIsCutToFitAField() {
        cache(message("5", "x".repeat(2_000)));

        listener.messageDeleted("5", channel);

        assertEquals(MessageEmbed.VALUE_MAX_LENGTH, fields(logged.getFirst()).get("Content").length());
    }

    @Test
    void aMessageWithoutTextStillLogs() {
        cache(message("5", ""));

        listener.messageDeleted("5", channel);

        assertEquals("None", fields(logged.getFirst()).get("Content"));
    }

    @Test
    void aMessageModeratorsCantSeeIsDroppedWithoutLogging() {
        moderatorsCanSee = false;
        cache(message("5", "hidden"));

        listener.messageDeleted("5", channel);

        assertTrue(logged.isEmpty());
        assertEquals(Optional.empty(), messageCache.get("5"));
    }

    @Test
    void anUncachedDeletedMessageIsNotLogged() {
        listener.messageDeleted("5", channel);

        assertTrue(logged.isEmpty());
    }

    @Test
    void anEditIsLoggedWithBothVersions() {
        cache(message("5", "before"));
        Message after = message("5", "after");

        listener.messageEdited(after, channel);

        Map<String, String> fields = fields(logged.getFirst());
        assertEquals("before", fields.get("Before"));
        assertEquals("after", fields.get("After"));
        assertSame(after, messageCache.get("5").orElseThrow());
    }

    @Test
    void anEditThatKeepsTheTextIsNotLogged() {
        cache(message("5", "same"));
        Message after = message("5", "same");

        listener.messageEdited(after, channel);

        assertTrue(logged.isEmpty());
        assertSame(after, messageCache.get("5").orElseThrow());
    }

    @Test
    void anEditToAnUncachedMessageIsNotLoggedOrCached() {
        listener.messageEdited(message("5", "after"), channel);

        assertTrue(logged.isEmpty());
        assertEquals(Optional.empty(), messageCache.get("5"));
    }

    @Test
    void anInviteWithoutAnInviterOrLimitsIsDescribed() {
        Invite invite = Fakes.of(Invite.class, Map.of(
            "getUrl", "https://discord.gg/abc", "getTimeCreated", OffsetDateTime.parse("2026-10-12T12:00:00Z"), "getMaxAge", 0, "getMaxUses", 0));

        String description = ModLogListener.inviteCreated(invite).getDescription();

        assertTrue(description.contains("Created by Unknown"), description);
        assertTrue(description.contains("Channel: Unknown"), description);
        assertTrue(description.contains("Max Uses: Unlimited"), description);
        assertTrue(description.contains("Expires: Never"), description);
    }

    @Test
    void anExpiringInviteSaysWhen() {
        Invite invite = Fakes.of(Invite.class, Map.of(
            "getUrl", "https://discord.gg/abc", "getTimeCreated", OffsetDateTime.parse("2026-10-12T12:00:00Z"),
            "getMaxAge", 7 * 24 * 60 * 60, "getMaxUses", 5, "getInviter", user("1", false)));

        String description = ModLogListener.inviteCreated(invite).getDescription();

        assertTrue(description.contains("Created by <@1>"), description);
        assertTrue(description.contains("Max Uses: 5"), description);
        assertTrue(description.contains("Expires: after 7d"), description);
    }

    @Test
    void durationsAreWrittenShort() {
        assertEquals("1d 1h", ModLogListener.describe(Duration.ofHours(25)));
        assertEquals("30m", ModLogListener.describe(Duration.ofMinutes(30)));
        assertEquals("1h 5s", ModLogListener.describe(Duration.ofSeconds(3605)));
        assertEquals("0s", ModLogListener.describe(Duration.ZERO));
    }

    @Test
    void blankFieldValuesSayNone() {
        assertEquals("None", ModLogListener.fieldValue(null));
        assertEquals("None", ModLogListener.fieldValue("  "));
        assertEquals("text", ModLogListener.fieldValue("text"));
    }
}
