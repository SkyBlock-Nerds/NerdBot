package net.hypixel.nerdbot.app.urlwatcher.handler.update;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.hypixel.nerdbot.app.role.RoleManager;
import net.hypixel.nerdbot.core.xml.SkyBlockThreadParser.HypixelThread;
import net.hypixel.nerdbot.discord.cache.ChannelCache;
import net.hypixel.nerdbot.discord.config.channel.ChannelConfig;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

@Slf4j
public class SkyBlockUpdateDataHandler {

    private SkyBlockUpdateDataHandler() {
    }

    public static void handleThread(HypixelThread hypixelThread) {
        ChannelConfig config = DiscordBotEnvironment.getBot().getConfig().getChannelConfig();

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
        }, () -> log.warn("Could not find announcement channel!"));
    }
}
