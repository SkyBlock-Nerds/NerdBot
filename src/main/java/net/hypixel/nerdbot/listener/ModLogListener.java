package net.hypixel.nerdbot.listener;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
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
import net.hypixel.nerdbot.channel.ChannelManager;

import java.awt.*;
import java.time.Instant;
import java.time.LocalTime;

public class ModLogListener {

    @SubscribeEvent
    public void onJoin(GuildMemberJoinEvent event) {
        if (ChannelManager.getLogChannel() == null) {
            return;
        }

        Member member = event.getMember();
        MessageEmbed messageEmbed = getDefaultEmbed()
            .setTitle("Member joined")
            .setDescription(member.getAsMention())
            .setThumbnail(member.getAvatarUrl())
            .setColor(Color.GREEN)
            .build();

        ChannelManager.getLogChannel().sendMessageEmbeds(messageEmbed).queue();
    }

    @SubscribeEvent
    public void onMemberRemove(GuildMemberRemoveEvent event) {
        if (ChannelManager.getLogChannel() == null) {
            return;
        }

        Member member = event.getMember();
        MessageEmbed messageEmbed = getDefaultEmbed()
            .setTitle("Member removed")
            .setDescription(member.getAsMention())
            .setThumbnail(member.getAvatarUrl())
            .setColor(Color.BLUE)
            .build();

        ChannelManager.getLogChannel().sendMessageEmbeds(messageEmbed).queue();
    }

    @SubscribeEvent
    public void onGuildBan(GuildBanEvent event) {
        if (ChannelManager.getLogChannel() == null) {
            return;
        }

        User member = event.getUser();
        MessageEmbed messageEmbed = getDefaultEmbed()
            .setTitle("Member banned")
            .setDescription(member.getAsMention())
            .setThumbnail(member.getAvatarUrl())
            .setColor(Color.RED)
            .build();

        ChannelManager.getLogChannel().sendMessageEmbeds(messageEmbed).queue();
    }

    @SubscribeEvent
    public void onGuildUnban(GuildUnbanEvent event) {
        if (ChannelManager.getLogChannel() == null) {
            return;
        }

        User member = event.getUser();
        MessageEmbed messageEmbed = getDefaultEmbed()
            .setTitle("Member unbanned")
            .setDescription(member.getAsMention())
            .setThumbnail(member.getAvatarUrl())
            .setColor(Color.GREEN)
            .build();

        ChannelManager.getLogChannel().sendMessageEmbeds(messageEmbed).queue();
    }

    @SubscribeEvent
    public void onInviteCreate(GuildInviteCreateEvent event) {
        if (ChannelManager.getLogChannel() == null) {
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

        ChannelManager.getLogChannel().sendMessageEmbeds(messageEmbed).queue();
    }

    @SubscribeEvent
    public void onInviteDelete(GuildInviteDeleteEvent event) {
        if (ChannelManager.getLogChannel() == null) {
            return;
        }

        MessageEmbed messageEmbed = getDefaultEmbed()
            .setTitle("Invite deleted")
            .setDescription("Invite Code: " + event.getUrl())
            .setColor(Color.RED)
            .build();

        ChannelManager.getLogChannel().sendMessageEmbeds(messageEmbed).queue();
    }

    @SubscribeEvent
    public void onRoleAdd(GuildMemberRoleAddEvent event) {
        if (ChannelManager.getLogChannel() == null) {
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
        ChannelManager.getLogChannel().sendMessageEmbeds(messageEmbed).queue();
    }

    @SubscribeEvent
    public void onRoleRemove(GuildMemberRoleRemoveEvent event) {
        if (ChannelManager.getLogChannel() == null) {
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

        ChannelManager.getLogChannel().sendMessageEmbeds(messageEmbed).queue();
    }

    @SubscribeEvent
    public void onMessageDelete(MessageDeleteEvent event) {
        if (ChannelManager.getLogChannel() == null) {
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
            .addField("Content", message.getContentDisplay(), false);

        if (!message.getAttachments().isEmpty()) {
            StringBuilder attachments = new StringBuilder();
            message.getAttachments().forEach(attachment -> attachments.append(attachment.getUrl()).append("\n"));
            messageEmbed.addField("Attachments", attachments.toString(), false);
        }

        ChannelManager.getLogChannel().sendMessageEmbeds(messageEmbed.build()).queue();
    }

    @SubscribeEvent
    public void onMessageEdit(MessageUpdateEvent event) {
        if (ChannelManager.getLogChannel() == null || event.getAuthor().getId().equals(NerdBotApp.getBot().getJDA().getSelfUser().getId())) {
            return;
        }

        Message before = NerdBotApp.getMessageCache().getMessage(event.getMessageId());
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

        ChannelManager.getLogChannel().sendMessageEmbeds(messageEmbed).queue();
    }

    private EmbedBuilder getDefaultEmbed() {
        return new EmbedBuilder().setTimestamp(Instant.now());
    }
}
