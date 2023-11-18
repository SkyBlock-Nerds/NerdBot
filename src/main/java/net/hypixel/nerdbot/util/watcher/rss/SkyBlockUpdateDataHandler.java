package net.hypixel.nerdbot.util.watcher.rss;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.ChannelConfig;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.role.PingableRole;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.watcher.rss.xmlparsers.SkyBlockThreadParser.HypixelThread;

@Log4j2
public class SkyBlockUpdateDataHandler {

    private SkyBlockUpdateDataHandler() {
    }

    public static void handleThread(HypixelThread hypixelThread) {
        ChannelConfig config = NerdBotApp.getBot().getConfig().getChannelConfig();

        ChannelManager.getChannel(config.getAnnouncementChannelId()).ifPresentOrElse(textChannel -> {
            // Simple Check to make sure only SkyBlock threads are sent.
            if (!hypixelThread.getForum().equals("SkyBlock Patch Notes")){
                if (!hypixelThread.getTitle().contains("SkyBlock")){
                    return;
                }
            }

            MessageCreateBuilder messageCreateBuilder = new MessageCreateBuilder();
            PingableRole role = RoleManager.getPingableRoleByName("SkyBlock Update Alerts");
            if (role != null) {
                messageCreateBuilder.addContent(RoleManager.formatPingableRoleAsMention(role) + "\n\n");
            }

            messageCreateBuilder.addContent(hypixelThread.getLink());
            textChannel.sendMessage(messageCreateBuilder.build()).queue();
        }, () -> {
            throw new IllegalStateException("Could not find announcement channel!");
        });
    }
}
