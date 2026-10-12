package net.hypixel.nerdbot.app.listener;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.IPermissionContainerUnion;
import net.hypixel.nerdbot.app.channel.RestrictedChannelGroups;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.testsupport.FakeConfigStore;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleRestrictedChannelListenerTest {

    private final NerdBotConfig config = new NerdBotConfig();
    private final List<String> refreshed = new ArrayList<>();
    private final RestrictedChannelGroups groups = new RestrictedChannelGroups(() -> config, new FakeConfigStore(config)) {
        @Override
        public synchronized boolean refresh(GuildChannel channel) {
            refreshed.add(channel.getId());
            return false;
        }
    };
    private final RoleRestrictedChannelListener listener = new RoleRestrictedChannelListener(new GuildScope(() -> config), groups);

    private static GuildChannel channel(String id, ChannelType type) {
        return Fakes.of(GuildChannel.class, Map.of("getId", id, "getType", type));
    }

    @Test
    void aChannelsOwnPermissionChangeRechecksIt() {
        listener.permissionsChanged(Fakes.of(IPermissionContainerUnion.class, Map.of("getId", "10", "getType", ChannelType.TEXT)));

        assertEquals(List.of("10"), refreshed);
    }

    @Test
    void aCategorysPermissionChangeRechecksItsTrackedChannels() {
        Category category = Fakes.of(Category.class, Map.of("getChannels", List.of(
            channel("10", ChannelType.TEXT), channel("11", ChannelType.VOICE), channel("12", ChannelType.FORUM), channel("13", ChannelType.STAGE))));

        listener.permissionsChanged(Fakes.of(IPermissionContainerUnion.class, Map.of(
            "getId", "1", "getType", ChannelType.CATEGORY, "asCategory", category)));

        assertEquals(List.of("10", "11", "12"), refreshed);
    }

    @Test
    void untrackedChannelsAreNotRechecked() {
        listener.permissionsChanged(Fakes.of(IPermissionContainerUnion.class, Map.of("getId", "10", "getType", ChannelType.STAGE)));

        assertEquals(List.of(), refreshed);
    }
}
