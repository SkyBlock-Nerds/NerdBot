package net.hypixel.nerdbot.app.listener;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static net.hypixel.nerdbot.app.testsupport.MemberFakes.MAIN_GUILD_ID;
import static net.hypixel.nerdbot.app.testsupport.MemberFakes.member;
import static net.hypixel.nerdbot.app.testsupport.ThreadFakes.forumParent;
import static net.hypixel.nerdbot.app.testsupport.ThreadFakes.textChannel;
import static net.hypixel.nerdbot.app.testsupport.ThreadFakes.thread;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsListenerTest {

    private static double messages(String channelName) {
        return PrometheusMetrics.TOTAL_MESSAGES_AMOUNT.labels("none", channelName).get();
    }

    private static ForumChannel forumWithTags(String... names) {
        List<ForumTag> tags = Arrays.stream(names).map(name -> Fakes.of(ForumTag.class, Map.of("getName", name))).toList();
        return Fakes.of(ForumChannel.class, Map.of("getAvailableTags", tags));
    }

    @Test
    void threadMessagesAreCountedAgainstTheirChannel() {
        double before = messages("metrics-forum");

        MetricsListener.countMessage(member("1"), thread("20", forumParent("10", "metrics-forum")));
        MetricsListener.countMessage(member("1"), thread("21", forumParent("10", "metrics-forum")));

        assertEquals(before + 2, messages("metrics-forum"));
    }

    @Test
    void channelMessagesAreCountedAgainstTheChannel() {
        double before = messages("metrics-general");

        MetricsListener.countMessage(member("1"), textChannel("11", "metrics-general"));

        assertEquals(before + 1, messages("metrics-general"));
    }

    @Test
    void forumTagCountsStartFromTheMainGuildsForums() {
        NerdBotConfig config = new NerdBotConfig();
        config.setGuildId(MAIN_GUILD_ID);
        Guild main = Fakes.of(Guild.class, Map.of("getId", MAIN_GUILD_ID, "getForumChannels", List.of(
            forumWithTags("metrics-tag-a", "metrics-tag-b"), forumWithTags("metrics-tag-a"))));
        Guild other = Fakes.of(Guild.class, Map.of("getId", "200", "getForumChannels", List.of(forumWithTags("metrics-tag-c"))));

        new MetricsListener(new GuildScope(() -> config), Fakes.of(JDA.class, Map.of("getGuilds", List.of(main, other))));

        assertEquals(2, PrometheusMetrics.FORUM_TAG_AMOUNT.labels("metrics-tag-a").get());
        assertEquals(1, PrometheusMetrics.FORUM_TAG_AMOUNT.labels("metrics-tag-b").get());
        assertEquals(0, PrometheusMetrics.FORUM_TAG_AMOUNT.labels("metrics-tag-c").get());
    }
}
