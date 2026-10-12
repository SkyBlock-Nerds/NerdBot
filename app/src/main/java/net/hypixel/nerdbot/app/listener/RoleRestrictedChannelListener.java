package net.hypixel.nerdbot.app.listener;

import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.IPermissionContainerUnion;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateParentEvent;
import net.dv8tion.jda.api.events.guild.override.GenericPermissionOverrideEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideCreateEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideDeleteEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideUpdateEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.app.channel.RestrictedChannelGroups;

/** Keeps the role-restricted channel groups in step as channels and their permissions change. */
public class RoleRestrictedChannelListener {

    private final GuildScope guildScope;
    private final RestrictedChannelGroups groups;

    @Inject
    public RoleRestrictedChannelListener(GuildScope guildScope, RestrictedChannelGroups groups) {
        this.guildScope = guildScope;
        this.groups = groups;
    }

    @SubscribeEvent
    public void onChannelCreate(ChannelCreateEvent event) {
        if (event.isFromGuild() && guildScope.isMainGuild(event.getGuild())) {
            refresh(event.getChannel().asGuildChannel());
        }
    }

    // A channel synced to its category takes the new category's permissions
    @SubscribeEvent
    public void onChannelParentUpdate(ChannelUpdateParentEvent event) {
        if (event.isFromGuild() && guildScope.isMainGuild(event.getGuild())) {
            refresh(event.getChannel().asGuildChannel());
        }
    }

    @SubscribeEvent
    public void onChannelDelete(ChannelDeleteEvent event) {
        if (event.isFromGuild() && guildScope.isMainGuild(event.getGuild()) && RestrictedChannelGroups.isTracked(event.getChannel().asGuildChannel())) {
            groups.forget(event.getChannel().asGuildChannel());
        }
    }

    @SubscribeEvent
    public void onPermissionOverrideCreate(PermissionOverrideCreateEvent event) {
        overridesChanged(event);
    }

    @SubscribeEvent
    public void onPermissionOverrideUpdate(PermissionOverrideUpdateEvent event) {
        overridesChanged(event);
    }

    @SubscribeEvent
    public void onPermissionOverrideDelete(PermissionOverrideDeleteEvent event) {
        overridesChanged(event);
    }

    private void overridesChanged(GenericPermissionOverrideEvent event) {
        if (guildScope.isMainGuild(event.getGuild())) {
            permissionsChanged(event.getChannel());
        }
    }

    /** A category's permissions reach the channels synced to it, so each of its channels is rechecked. */
    void permissionsChanged(IPermissionContainerUnion channel) {
        if (channel.getType() == ChannelType.CATEGORY) {
            channel.asCategory().getChannels().forEach(this::refresh);
        } else {
            refresh(channel);
        }
    }

    private void refresh(GuildChannel channel) {
        if (RestrictedChannelGroups.isTracked(channel)) {
            groups.refresh(channel);
        }
    }
}
