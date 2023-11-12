package net.hypixel.nerdbot.util.watcher.rss;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.ChannelConfig;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.role.PingableRole;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.watcher.rss.xmlparsers.SkyblockThreadParser.HypixelThread;

@Log4j2
public class SkyblockUpdateDataHandler {

    public static void handleThread(HypixelThread hypixelThread) {
        ChannelConfig config = NerdBotApp.getBot().getConfig().getChannelConfig();
        TextChannel announcementChannel = ChannelManager.getChannel(config.getAnnouncementChannelId());

        if (announcementChannel == null) {
            log.error("Couldn't find announcement channel!");
            return;
        }


        MessageCreateBuilder messageCreateBuilder = new MessageCreateBuilder();

        PingableRole role = RoleManager.getPingableRoleByName("SkyBlock Update Alerts");

        if (role != null) {
            messageCreateBuilder.addContent(RoleManager.formatPingableRoleAsMention(role) + "\n\n");
        }

        messageCreateBuilder.addContent(hypixelThread.getLink());

        announcementChannel.sendMessage(messageCreateBuilder.build()).queue();
    }
}
