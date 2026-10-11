package net.hypixel.nerdbot.app.listener;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideCreateEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideDeleteEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideUpdateEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.app.channel.RestrictedChannelGroups;

import java.util.function.Consumer;

@Slf4j
public class RoleRestrictedChannelListener {

    @SubscribeEvent
    public void onChannelCreate(ChannelCreateEvent event) {
        if (event.isFromGuild()) {
            update(event.getChannel().asGuildChannel(), groups -> groups.refresh(event.getChannel().asGuildChannel()));
        }
    }

    @SubscribeEvent
    public void onChannelDelete(ChannelDeleteEvent event) {
        if (event.isFromGuild()) {
            update(event.getChannel().asGuildChannel(), groups -> groups.forget(event.getChannel().asGuildChannel()));
        }
    }

    @SubscribeEvent
    public void onPermissionOverrideCreate(PermissionOverrideCreateEvent event) {
        update(event.getChannel(), groups -> groups.refresh(event.getChannel()));
    }

    @SubscribeEvent
    public void onPermissionOverrideUpdate(PermissionOverrideUpdateEvent event) {
        update(event.getChannel(), groups -> groups.refresh(event.getChannel()));
    }

    @SubscribeEvent
    public void onPermissionOverrideDelete(PermissionOverrideDeleteEvent event) {
        update(event.getChannel(), groups -> groups.refresh(event.getChannel()));
    }

    private static void update(GuildChannel channel, Consumer<RestrictedChannelGroups> change) {
        if (!RestrictedChannelGroups.isTracked(channel)) {
            return;
        }

        // Channel events can arrive before the bot has finished starting
        SkyBlockNerdsBot.restrictedChannelGroups().ifPresentOrElse(change,
            () -> log.debug("Skipped a role-restricted group update for {} because the bot isn't ready", channel.getId()));
    }
}
