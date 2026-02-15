package net.hypixel.nerdbot.app.listener;

import lombok.extern.slf4j.Slf4j;
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
import net.hypixel.nerdbot.app.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.discord.role.RoleManager;
import net.hypixel.nerdbot.marmalade.collections.ArrayUtils;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

@Slf4j
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
            DiscordBotConfig botConfig = DiscordBotEnvironment.getBot().getConfig();

            if (forumChannelId.equals(botConfig.getSuggestionConfig().getForumChannelId())
                || ArrayUtils.safeArrayStream(botConfig.getAlphaProjectConfig().getAlphaForumIds(), botConfig.getAlphaProjectConfig().getProjectForumIds()).anyMatch(forumChannelId::equals)) {
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
