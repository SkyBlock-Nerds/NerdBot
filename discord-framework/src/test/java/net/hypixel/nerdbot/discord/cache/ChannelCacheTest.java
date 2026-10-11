package net.hypixel.nerdbot.discord.cache;

import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelCacheTest {

    private final List<String> cachedIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        cachedIds.forEach(ChannelCache::uncache);
    }

    private <T extends GuildChannel> T cached(Class<T> type, String id, String name) {
        T channel = Fakes.of(type, Map.of("getId", id, "getName", name));
        ChannelCache.cache(channel);
        cachedIds.add(id);
        return channel;
    }

    @Test
    void textChannelLookupsOnlyReturnTextChannels() {
        TextChannel text = cached(TextChannel.class, "cc-text", "cc-general");
        cached(ForumChannel.class, "cc-forum", "cc-suggestions");

        assertSame(text, ChannelCache.getTextChannelById("cc-text").orElseThrow());
        assertTrue(ChannelCache.getTextChannelById("cc-forum").isEmpty());
        assertTrue(ChannelCache.getTextChannelByName("cc-suggestions").isEmpty());
    }

    @Test
    void forumAndCategoryLookupsAreTypeChecked() {
        ForumChannel forum = cached(ForumChannel.class, "cc-forum2", "cc-ideas");
        Category category = cached(Category.class, "cc-category", "cc-archive");

        assertSame(forum, ChannelCache.getForumChannelById("cc-forum2").orElseThrow());
        assertSame(category, ChannelCache.getCategoryByName("cc-archive").orElseThrow());
        assertTrue(ChannelCache.getForumChannelById("cc-category").isEmpty());
        assertTrue(ChannelCache.getCategoryById("cc-forum2").isEmpty());
    }

    @Test
    void missingOrBlankIdsAreEmptyRatherThanErrors() {
        assertTrue(ChannelCache.getChannelById(null).isEmpty());
        assertTrue(ChannelCache.getChannelById("").isEmpty());
        assertTrue(ChannelCache.getTextChannelById("  ").isEmpty());
        assertTrue(ChannelCache.getChannelById("cc-never-cached").isEmpty());
    }

    @Test
    void uncachedChannelsAreGone() {
        cached(TextChannel.class, "cc-temp", "cc-temp");

        ChannelCache.uncache("cc-temp");

        assertTrue(ChannelCache.getChannelById("cc-temp").isEmpty());
    }

    @Test
    void cachingAnUpdatedChannelReplacesTheOldOne() {
        cached(TextChannel.class, "cc-renamed", "cc-old-name");
        TextChannel renamed = cached(TextChannel.class, "cc-renamed", "cc-new-name");

        assertSame(renamed, ChannelCache.getChannelById("cc-renamed").orElseThrow());
        assertEquals("cc-new-name", ChannelCache.getTextChannelById("cc-renamed").orElseThrow().getName());
    }
}
