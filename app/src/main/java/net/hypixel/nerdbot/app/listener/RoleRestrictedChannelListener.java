package net.hypixel.nerdbot.app.listener;

import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideCreateEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideDeleteEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideUpdateEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.app.channel.RestrictedChannelGroups;

public class RoleRestrictedChannelListener {

    private final RestrictedChannelGroups groups;

    @Inject
    public RoleRestrictedChannelListener(RestrictedChannelGroups groups) {
        this.groups = groups;
    }

    @SubscribeEvent
    public void onChannelCreate(ChannelCreateEvent event) {
        if (event.isFromGuild()) {
            refresh(event.getChannel().asGuildChannel());
        }
    }

    @SubscribeEvent
    public void onChannelDelete(ChannelDeleteEvent event) {
        if (event.isFromGuild() && RestrictedChannelGroups.isTracked(event.getChannel().asGuildChannel())) {
            groups.forget(event.getChannel().asGuildChannel());
        }
    }

    @SubscribeEvent
    public void onPermissionOverrideCreate(PermissionOverrideCreateEvent event) {
        refresh(event.getChannel());
    }

    @SubscribeEvent
    public void onPermissionOverrideUpdate(PermissionOverrideUpdateEvent event) {
        refresh(event.getChannel());
    }

    @SubscribeEvent
    public void onPermissionOverrideDelete(PermissionOverrideDeleteEvent event) {
        refresh(event.getChannel());
    }

    private void refresh(GuildChannel channel) {
        if (RestrictedChannelGroups.isTracked(channel)) {
            groups.refresh(channel);
        }
    }
}
