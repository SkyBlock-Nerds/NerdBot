package net.hypixel.skyblocknerds.discordbot.listener;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.hooks.SubscribeEvent;

@Log4j2
public class AuditLogListener extends ListenerAdapter {

    @SubscribeEvent
    public void onMessageEdit(MessageUpdateEvent event) {
        log.info("Message edited: " + event.getMessageId());
    }

    @SubscribeEvent
    public void onMemberRoleAdd(GuildMemberRoleAddEvent event) {
        log.info("Role added to member: " + event.getMember().getId());
    }

    @SubscribeEvent
    public void onMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        log.info("Role removed from member: " + event.getMember().getId());
    }
}
