package net.hypixel.nerdbot.listener;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteDeleteEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.cache.ChannelCache;

import java.awt.Color;
import java.time.Instant;
import java.time.LocalTime;

public class ModLogListener {

    @SubscribeEvent
    public void onJoin(GuildMemberJoinEvent event) {
        if (ChannelCache.getLogChannel().isEmpty()) {
            return;
        }

        Member member = event.getMember();
        String roles = member.getRoles().stream().map(Role::getName).reduce((a, b) -> a + ", " + b).orElse("None");
        MessageEmbed messageEmbed = getDefaultEmbed()
            .setTitle("Member joined")
            .addField("Member ID", member.getId(), false)
            .addField("Member Name", member.getEffectiveName(), false)
            .addField("Join Date", member.getTimeJoined().toString(), false)
            .addField("Member Roles", roles, false)
            .setThumbnail(member.getAvatarUrl())
            .setColor(Color.GREEN)
            .build();

        ChannelCache.getLogChannel().get().sendMessageEmbeds(messageEmbed).queue();
    }

    @SubscribeEvent
    public void onMemberRemove(GuildMemberRemoveEvent event) {
        if (ChannelCache.getLogChannel().isEmpty()) {
            return;
        }

        Member member = event.getMember();

        if (member == null) {
            ChannelCache.getLogChannel().get().sendMessage("Could not find member with ID " + event.getUser().getId() + " who left the server!").queue();
            return;
        }

        String roles = member.getRoles().stream().map(Role::getName).reduce((a, b) -> a + ", " + b).orElse("None");
        MessageEmbed messageEmbed = getDefaultEmbed()
            .setTitle("Member removed")
            .addField("Member ID", member.getId(), false)
            .addField("Member Name", member.getEffectiveName(), false)
            .addField("Join Date", member.getTimeJoined().toString(), false)
            .addField("Member Roles", roles, false)
            .setThumbnail(member.getAvatarUrl())
            .setColor(Color.BLUE)
            .build();

        ChannelCache.getLogChannel().get().sendMessageEmbeds(messageEmbed).queue();
    }

    @SubscribeEvent
    public void onGuildBan(GuildBanEvent event) {
        if (ChannelCache.getLogChannel().isEmpty()) {
            return;
        }

        User member = event.getUser();
        MessageEmbed messageEmbed = getDefaultEmbed()
            .setTitle("Member banned")
            .setDescription(member.getAsMention())
            .setThumbnail(member.getAvatarUrl())
            .setColor(Color.RED)
            .build();

        ChannelCache.getLogChannel().get().sendMessageEmbeds(messageEmbed).queue();
    }

    @SubscribeEvent
    public void onGuildUnban(GuildUnbanEvent event) {
        if (ChannelCache.getLogChannel().isEmpty()) {
            return;
        }

        User member = event.getUser();
        MessageEmbed messageEmbed = getDefaultEmbed()
            .setTitle("Member unbanned")
            .setDescription(member.getAsMention())
            .setThumbnail(member.getAvatarUrl())
            .setColor(Color.GREEN)
            .build();

        ChannelCache.getLogChannel().get().sendMessageEmbeds(messageEmbed).queue();
    }

    @SubscribeEvent
    public void onInviteCreate(GuildInviteCreateEvent event) {
        if (ChannelCache.getLogChannel().isEmpty()) {
            return;
        }

        User member = event.getInvite().getInviter();
        Invite invite = event.getInvite();
        MessageEmbed messageEmbed = getDefaultEmbed()
            .setTitle("Invite created")
            .setDescription("Created by " + member.getAsMention()
                + "\n\nInvite URL: " + invite.getUrl()
                + "\nTime created: " + invite.getTimeCreated()
                + "\nChannel: " + invite.getChannel().getName()
                + "\nMax Uses: " + invite.getMaxUses()
                + "\nMax Age: " + LocalTime.ofSecondOfDay(Math.min(86_399, invite.getMaxAge())).toString()
                + "\nTemporary? " + (invite.isTemporary() ? "Yes" : "No"))
            .setThumbnail(member.getAvatarUrl())
            .setColor(Color.GREEN)
            .build();

        ChannelCache.getLogChannel().get().sendMessageEmbeds(messageEmbed).queue();
    }

    @SubscribeEvent
    public void onInviteDelete(GuildInviteDeleteEvent event) {
        if (ChannelCache.getLogChannel().isEmpty()) {
            return;
        }

        MessageEmbed messageEmbed = getDefaultEmbed()
            .setTitle("Invite deleted")
            .setDescription("Invite Code: " + event.getUrl())
            .setColor(Color.RED)
            .build();

        ChannelCache.getLogChannel().get().sendMessageEmbeds(messageEmbed).queue();
    }

    @SubscribeEvent
    public void onRoleAdd(GuildMemberRoleAddEvent event) {
        if (ChannelCache.getLogChannel().isEmpty()) {
            return;
        }

        Member member = event.getMember();
        StringBuilder stringBuilder = new StringBuilder("Roles added to " + member.getAsMention() + ":\n");

        for (Role role : event.getRoles()) {
            stringBuilder.append(" • ").append(role.getName()).append("\n");
        }

        MessageEmbed messageEmbed = getDefaultEmbed()
            .setTitle("Role(s) added")
            .setDescription(stringBuilder.toString())
            .setThumbnail(member.getAvatarUrl())
            .setColor(Color.GREEN)
            .build();
        ChannelCache.getLogChannel().get().sendMessageEmbeds(messageEmbed).queue();
    }

    @SubscribeEvent
    public void onRoleRemove(GuildMemberRoleRemoveEvent event) {
        if (ChannelCache.getLogChannel().isEmpty()) {
            return;
        }

        Member member = event.getMember();
        StringBuilder stringBuilder = new StringBuilder("Roles removed from " + member.getAsMention() + ":\n");
        event.getRoles().forEach(role -> stringBuilder.append(" • ").append(role.getName()).append("\n"));
        MessageEmbed messageEmbed = getDefaultEmbed()
            .setTitle("Role(s) removed")
            .setDescription(stringBuilder.toString())
            .setThumbnail(member.getAvatarUrl())
            .setColor(Color.RED)
            .build();

        ChannelCache.getLogChannel().get().sendMessageEmbeds(messageEmbed).queue();
    }

    @SubscribeEvent
    public void onMessageDelete(MessageDeleteEvent event) {
        if (ChannelCache.getLogChannel().isEmpty()) {
            return;
        }

        Message message = NerdBotApp.getMessageCache().getMessage(event.getMessageId());
        if (message == null) {
            return;
        }

        User user = message.getAuthor();
        Channel channel = message.getChannel();
        EmbedBuilder messageEmbed = getDefaultEmbed()
            .setTitle("Message deleted")
            .setThumbnail(user.getAvatarUrl())
            .setColor(Color.RED)
            .addField("User", user.getAsMention(), false)
            .addField("Channel", channel.getAsMention(), false)
            .addField("User ID", user.getId(), false)
            .addField("Content", message.getContentDisplay().substring(0, 2_000), false);

        if (!message.getAttachments().isEmpty()) {
            StringBuilder attachments = new StringBuilder();
            message.getAttachments().forEach(attachment -> attachments.append(attachment.getUrl()).append("\n"));
            messageEmbed.addField("Attachments", attachments.toString(), false);
        }

        ChannelCache.getLogChannel().get().sendMessageEmbeds(messageEmbed.build()).queue();
    }

    @SubscribeEvent
    public void onMessageEdit(MessageUpdateEvent event) {
        if (ChannelCache.getLogChannel().isEmpty() || event.getAuthor().getId().equals(NerdBotApp.getBot().getJDA().getSelfUser().getId())) {
            return;
        }

        Message before = NerdBotApp.getMessageCache().getMessage(event.getMessageId());
        if (before == null) {
            return;
        }

        Message after = event.getMessage();
        User user = before.getAuthor();
        Channel channel = before.getChannel();
        MessageEmbed messageEmbed = getDefaultEmbed()
            .setTitle("Message edited")
            .setThumbnail(user.getAvatarUrl())
            .setColor(Color.YELLOW)
            .addField("User", user.getAsMention(), false)
            .addField("Channel", channel.getAsMention(), false)
            .addField("User ID", user.getId(), false)
            .addField("Before", before.getContentDisplay(), true)
            .addField("After", after.getContentDisplay(), true)
            .build();

        ChannelCache.getLogChannel().get().sendMessageEmbeds(messageEmbed).queue();
    }

    private EmbedBuilder getDefaultEmbed() {
        return new EmbedBuilder().setTimestamp(Instant.now());
    }
}
