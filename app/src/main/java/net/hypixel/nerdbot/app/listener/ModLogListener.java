package net.hypixel.nerdbot.app.listener;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
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
import net.dv8tion.jda.api.utils.TimeFormat;
import net.hypixel.nerdbot.app.channel.LogChannel;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.cache.MessageCache;
import net.hypixel.nerdbot.discord.util.StringUtils;
import net.hypixel.nerdbot.marmalade.discord.EmbedFactory;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Posts joins, leaves, bans, invites, role changes and deleted or edited messages to the staff log channel. */
public class ModLogListener {

    private static final String NONE = "None";
    private static final String UNKNOWN = "Unknown";

    private final GuildScope guildScope;
    private final LogChannel logChannel;
    private final MessageCache messageCache;
    private final Provider<NerdBotConfig> config;

    @Inject
    public ModLogListener(GuildScope guildScope, LogChannel logChannel, MessageCache messageCache, Provider<NerdBotConfig> config) {
        this.guildScope = guildScope;
        this.logChannel = logChannel;
        this.messageCache = messageCache;
        this.config = config;
    }

    @SubscribeEvent
    public void onJoin(GuildMemberJoinEvent event) {
        if (!guildScope.isMainGuild(event.getGuild())) {
            return;
        }

        Member member = event.getMember();
        logChannel.send(memberEmbed(EmbedFactory.success("Member joined", null), member).build());
    }

    @SubscribeEvent
    public void onMemberRemove(GuildMemberRemoveEvent event) {
        if (!guildScope.isMainGuild(event.getGuild())) {
            return;
        }

        Member member = event.getMember();
        EmbedBuilder embed = EmbedFactory.create("Member removed", null, Color.BLUE);
        if (member == null) {
            User user = event.getUser();
            embed.addField("Member ID", user.getId(), false)
                .addField("Member Name", user.getName(), false)
                .setFooter("They weren't cached, so their roles and join date are unknown")
                .setThumbnail(user.getEffectiveAvatarUrl());
        } else {
            memberEmbed(embed, member);
        }

        logChannel.send(embed.build());
    }

    private static EmbedBuilder memberEmbed(EmbedBuilder embed, Member member) {
        return embed
            .addField("Member ID", member.getId(), false)
            .addField("Member Name", fieldValue(member.getEffectiveName()), false)
            .addField("Join Date", TimeFormat.DATE_TIME_LONG.format(member.getTimeJoined()), false)
            .addField("Member Roles", fieldValue(roleNames(member.getRoles())), false)
            .setThumbnail(member.getEffectiveAvatarUrl());
    }

    @SubscribeEvent
    public void onGuildBan(GuildBanEvent event) {
        if (guildScope.isMainGuild(event.getGuild())) {
            User user = event.getUser();
            logChannel.send(EmbedFactory.create("Member banned", user.getAsMention() + " (" + user.getId() + ")", Color.RED)
                .setThumbnail(user.getEffectiveAvatarUrl())
                .build());
        }
    }

    @SubscribeEvent
    public void onGuildUnban(GuildUnbanEvent event) {
        if (guildScope.isMainGuild(event.getGuild())) {
            User user = event.getUser();
            logChannel.send(EmbedFactory.success("Member unbanned", user.getAsMention() + " (" + user.getId() + ")")
                .setThumbnail(user.getEffectiveAvatarUrl())
                .build());
        }
    }

    @SubscribeEvent
    public void onInviteCreate(GuildInviteCreateEvent event) {
        if (guildScope.isMainGuild(event.getGuild())) {
            logChannel.send(inviteCreated(event.getInvite()));
        }
    }

    static MessageEmbed inviteCreated(Invite invite) {
        User inviter = invite.getInviter();
        Invite.Channel channel = invite.getChannel();
        String description = "Created by " + (inviter == null ? UNKNOWN : inviter.getAsMention())
            + "\n\nInvite URL: " + invite.getUrl()
            + "\nTime created: " + TimeFormat.DATE_TIME_LONG.format(invite.getTimeCreated())
            + "\nChannel: " + (channel == null ? UNKNOWN : channel.getName())
            + "\nMax Uses: " + (invite.getMaxUses() == 0 ? "Unlimited" : invite.getMaxUses())
            + "\nExpires: " + (invite.getMaxAge() == 0 ? "Never" : "after " + describe(Duration.ofSeconds(invite.getMaxAge())))
            + "\nTemporary? " + (invite.isTemporary() ? "Yes" : "No");

        EmbedBuilder embed = EmbedFactory.success("Invite created", description);
        if (inviter != null) {
            embed.setThumbnail(inviter.getEffectiveAvatarUrl());
        }
        return embed.build();
    }

    static String describe(Duration duration) {
        List<String> parts = new ArrayList<>();
        if (duration.toDaysPart() > 0) {
            parts.add(duration.toDaysPart() + "d");
        }
        if (duration.toHoursPart() > 0) {
            parts.add(duration.toHoursPart() + "h");
        }
        if (duration.toMinutesPart() > 0) {
            parts.add(duration.toMinutesPart() + "m");
        }
        if (duration.toSecondsPart() > 0 || parts.isEmpty()) {
            parts.add(duration.toSecondsPart() + "s");
        }
        return String.join(" ", parts);
    }

    @SubscribeEvent
    public void onInviteDelete(GuildInviteDeleteEvent event) {
        if (guildScope.isMainGuild(event.getGuild())) {
            logChannel.send(EmbedFactory.create("Invite deleted", "Invite URL: " + event.getUrl(), Color.RED).build());
        }
    }

    @SubscribeEvent
    public void onRoleAdd(GuildMemberRoleAddEvent event) {
        if (guildScope.isMainGuild(event.getGuild())) {
            logChannel.send(roleChange(EmbedFactory.success("Role(s) added", null), "Roles added to ", event.getMember(), event.getRoles()));
        }
    }

    @SubscribeEvent
    public void onRoleRemove(GuildMemberRoleRemoveEvent event) {
        if (guildScope.isMainGuild(event.getGuild())) {
            logChannel.send(roleChange(EmbedFactory.create("Role(s) removed", null, Color.RED), "Roles removed from ", event.getMember(), event.getRoles()));
        }
    }

    private static MessageEmbed roleChange(EmbedBuilder embed, String heading, Member member, List<Role> roles) {
        String description = heading + member.getAsMention() + ":\n" + roles.stream()
            .map(role -> " • " + role.getName())
            .collect(Collectors.joining("\n"));
        return embed.setDescription(StringUtils.truncate(description, MessageEmbed.DESCRIPTION_MAX_LENGTH))
            .setThumbnail(member.getEffectiveAvatarUrl())
            .build();
    }

    @SubscribeEvent
    public void onMessageDelete(MessageDeleteEvent event) {
        if (event.isFromGuild() && guildScope.isMainGuild(event.getGuild())) {
            messageDeleted(event.getMessageId(), event.getGuildChannel());
        }
    }

    void messageDeleted(String messageId, GuildMessageChannel channel) {
        // Taken out even when it won't be logged, so the cache doesn't hold deleted messages
        messageCache.remove(messageId)
            .filter(message -> visibleToModerators(channel))
            .ifPresent(message -> {
                User author = message.getAuthor();
                EmbedBuilder embed = EmbedFactory.create("Message deleted", null, Color.RED)
                    .setThumbnail(author.getEffectiveAvatarUrl())
                    .addField("User", author.getAsMention(), false)
                    .addField("Channel", channel.getAsMention(), false)
                    .addField("User ID", author.getId(), false)
                    .addField("Content", fieldValue(message.getContentDisplay()), false);

                if (!message.getAttachments().isEmpty()) {
                    String attachments = message.getAttachments().stream().map(Message.Attachment::getUrl).collect(Collectors.joining("\n"));
                    embed.addField("Attachments", fieldValue(attachments), false);
                }

                logChannel.send(embed.build());
            });
    }

    @SubscribeEvent
    public void onMessageEdit(MessageUpdateEvent event) {
        if (event.isFromGuild() && guildScope.isMainGuild(event.getGuild())
            && !event.getAuthor().getId().equals(event.getJDA().getSelfUser().getId())) {
            messageEdited(event.getMessage(), event.getGuildChannel());
        }
    }

    void messageEdited(Message after, GuildMessageChannel channel) {
        messageCache.replace(after)
            .filter(before -> !before.getContentRaw().equals(after.getContentRaw()))
            .filter(before -> visibleToModerators(channel))
            .ifPresent(before -> {
                User author = before.getAuthor();
                logChannel.send(EmbedFactory.create("Message edited", null, Color.YELLOW)
                    .setThumbnail(author.getEffectiveAvatarUrl())
                    .addField("User", author.getAsMention(), false)
                    .addField("Channel", channel.getAsMention(), false)
                    .addField("User ID", author.getId(), false)
                    .addField("Before", fieldValue(before.getContentDisplay()), true)
                    .addField("After", fieldValue(after.getContentDisplay()), true)
                    .build());
            });
    }

    // Messages from channels moderators can't see stay out of the log
    private boolean visibleToModerators(GuildMessageChannel channel) {
        String moderatorRoleId = config.get().getRoleConfig().getModeratorRoleId();
        return channel.getGuild().getRoles().stream()
            .anyMatch(role -> role.getId().equals(moderatorRoleId) && role.hasAccess(channel));
    }

    private static String roleNames(List<Role> roles) {
        return roles.isEmpty() ? NONE : roles.stream().map(Role::getName).collect(Collectors.joining(", "));
    }

    // Discord rejects an empty field or one over 1024 characters, which would lose the whole entry
    static String fieldValue(@Nullable String text) {
        return text == null || text.isBlank() ? NONE : StringUtils.truncate(text, MessageEmbed.VALUE_MAX_LENGTH);
    }
}
