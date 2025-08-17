package net.hypixel.nerdbot.api.urlwatcher.handler;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.channel.ChannelConfig;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.xml.SkyBlockThreadParser.HypixelThread;

@Slf4j
public class SkyBlockUpdateDataHandler {

    private SkyBlockUpdateDataHandler() {
    }

    public static void handleThread(HypixelThread hypixelThread) {
        ChannelConfig config = NerdBotApp.getBot().getConfig().getChannelConfig();

        ChannelCache.getTextChannelById(config.getAnnouncementChannelId()).ifPresentOrElse(textChannel -> {
            // Simple Check to make sure only SkyBlock threads are sent.
            if (!hypixelThread.getForum().equals("SkyBlock Patch Notes") && (!hypixelThread.getTitle().contains("SkyBlock"))) {
                return;
            }

            MessageCreateBuilder messageCreateBuilder = new MessageCreateBuilder();

            RoleManager.getPingableRoleByName("SkyBlock Update Alerts").ifPresent(pingableRole -> {
                messageCreateBuilder.addContent(RoleManager.formatPingableRoleAsMention(pingableRole) + "\n\n");
            });

            messageCreateBuilder.addContent(hypixelThread.getLink());
            textChannel.sendMessage(messageCreateBuilder.build()).queue();
        }, () -> {
            throw new IllegalStateException("Could not find announcement channel!");
        });
    }
}
