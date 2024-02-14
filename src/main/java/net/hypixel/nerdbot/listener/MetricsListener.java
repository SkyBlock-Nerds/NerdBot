package net.hypixel.nerdbot.listener;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
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
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.BotConfig;
import net.hypixel.nerdbot.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.Util;

@Log4j2
public class MetricsListener {

    @SubscribeEvent
    public void onEvent(GenericEvent event) {
        PrometheusMetrics.EVENTS_AMOUNT.labels(event.getClass().getSimpleName()).inc();
    }

    @SubscribeEvent
    public void onMessageSent(MessageReceivedEvent event) {
        if (event.getMember() != null) {
            PrometheusMetrics.TOTAL_MESSAGES_AMOUNT.labels(event.getAuthor().getName(), RoleManager.getHighestRole(event.getMember()).getName(), event.getChannel().getName()).inc();
        }

        if (event.getChannel() instanceof ThreadChannel threadChannel) {
            String forumChannelId = threadChannel.getParentChannel().getId();
            BotConfig botConfig = NerdBotApp.getBot().getConfig();

            if (forumChannelId.equals(botConfig.getSuggestionConfig().getForumChannelId()) ||
                Util.safeArrayStream(botConfig.getAlphaProjectConfig().getAlphaForumIds(), botConfig.getAlphaProjectConfig().getProjectForumIds()).anyMatch(forumChannelId::equals)) {
                PrometheusMetrics.TOTAL_SUGGESTIONS_AMOUNT.inc();
            }
        }
    }

    @SubscribeEvent
    public void onForumTagAdd(ForumTagAddEvent event) {
        PrometheusMetrics.FORUM_TAG_AMOUNT.labels(event.getTag().getName()).inc();
    }

    @SubscribeEvent
    public void onForumTagRemove(ForumTagRemoveEvent event) {
        PrometheusMetrics.FORUM_TAG_AMOUNT.labels(event.getTag().getName()).dec();
    }

    @SubscribeEvent
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        PrometheusMetrics.SLASH_COMMANDS_AMOUNT.labels(event.getMember() == null ? "N/A" : event.getMember().getEffectiveName(), event.getFullCommandName()).inc();
    }

    @SubscribeEvent
    public void onMemberJoin(GuildMemberJoinEvent event) {
        PrometheusMetrics.TOTAL_USERS_AMOUNT.inc();
    }

    @SubscribeEvent
    public void onMemberLeave(GuildMemberRemoveEvent event) {
        PrometheusMetrics.TOTAL_USERS_AMOUNT.dec();
    }

    @SubscribeEvent
    public void onInviteCreate(GuildInviteCreateEvent event) {
        PrometheusMetrics.INVITES_CREATED_AMOUNT.labels(event.getCode()).inc();
    }

    @SubscribeEvent
    public void onInviteDelete(GuildInviteDeleteEvent event) {
        PrometheusMetrics.INVITES_DELETED_AMOUNT.labels(event.getCode()).inc();
    }
}
