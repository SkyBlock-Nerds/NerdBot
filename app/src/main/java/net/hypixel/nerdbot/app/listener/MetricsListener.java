package net.hypixel.nerdbot.app.listener;

import jakarta.inject.Inject;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.channel.forum.ForumTagAddEvent;
import net.dv8tion.jda.api.events.channel.forum.ForumTagRemoveEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteDeleteEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.app.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.discord.role.RoleManager;

import java.util.Map;
import java.util.stream.Collectors;

/** Feeds the Prometheus metrics, counting only the main guild's members, messages and forum tags. */
public class MetricsListener {

    private final GuildScope guildScope;

    @Inject
    public MetricsListener(GuildScope guildScope, JDA jda) {
        this.guildScope = guildScope;
        countForumTags(jda);
    }

    // The tag events only move the counts, so they start from the forums as they are
    private void countForumTags(JDA jda) {
        Map<String, Long> forumsPerTag = jda.getGuilds().stream()
            .filter(guildScope::isMainGuild)
            .flatMap(guild -> guild.getForumChannels().stream())
            .flatMap(forum -> forum.getAvailableTags().stream())
            .collect(Collectors.groupingBy(ForumTag::getName, Collectors.counting()));
        forumsPerTag.forEach((tag, forums) -> PrometheusMetrics.FORUM_TAG_AMOUNT.labels(tag).set(forums));
    }

    @SubscribeEvent
    public void onEvent(GenericEvent event) {
        PrometheusMetrics.EVENTS_AMOUNT.labels(event.getClass().getSimpleName()).inc();
    }

    @SubscribeEvent
    public void onMessageSent(MessageReceivedEvent event) {
        if (event.isFromGuild() && guildScope.isMainGuild(event.getGuild()) && event.getMember() != null) {
            countMessage(event.getMember(), event.getGuildChannel());
        }
    }

    static void countMessage(Member member, GuildMessageChannel channel) {
        Role highestRole = RoleManager.getHighestRole(member);
        // Labelled by the thread's channel, as a label per thread would grow without bound
        String channelName = channel instanceof ThreadChannel thread ? thread.getParentChannel().getName() : channel.getName();
        PrometheusMetrics.TOTAL_MESSAGES_AMOUNT.labels(highestRole == null ? "none" : highestRole.getName(), channelName).inc();
    }

    @SubscribeEvent
    public void onForumTagAdd(ForumTagAddEvent event) {
        if (guildScope.isMainGuild(event.getChannel().getGuild())) {
            PrometheusMetrics.FORUM_TAG_AMOUNT.labels(event.getTag().getName()).inc();
        }
    }

    @SubscribeEvent
    public void onForumTagRemove(ForumTagRemoveEvent event) {
        if (guildScope.isMainGuild(event.getChannel().getGuild())) {
            PrometheusMetrics.FORUM_TAG_AMOUNT.labels(event.getTag().getName()).dec();
        }
    }

    @SubscribeEvent
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        PrometheusMetrics.SLASH_COMMANDS_AMOUNT.labels(event.getFullCommandName()).inc();
    }

    @SubscribeEvent
    public void onMemberJoin(GuildMemberJoinEvent event) {
        if (guildScope.isMainGuild(event.getGuild())) {
            PrometheusMetrics.TOTAL_USERS_AMOUNT.inc();
        }
    }

    @SubscribeEvent
    public void onMemberLeave(GuildMemberRemoveEvent event) {
        if (guildScope.isMainGuild(event.getGuild())) {
            PrometheusMetrics.TOTAL_USERS_AMOUNT.dec();
        }
    }

    @SubscribeEvent
    public void onInviteCreate(GuildInviteCreateEvent event) {
        if (guildScope.isMainGuild(event.getGuild())) {
            PrometheusMetrics.INVITES_CREATED_AMOUNT.inc();
        }
    }

    @SubscribeEvent
    public void onInviteDelete(GuildInviteDeleteEvent event) {
        if (guildScope.isMainGuild(event.getGuild())) {
            PrometheusMetrics.INVITES_DELETED_AMOUNT.inc();
        }
    }
}
