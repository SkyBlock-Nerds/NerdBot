package net.hypixel.nerdbot.channel;

import net.dv8tion.jda.api.entities.TextChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.util.Logger;
import org.jetbrains.annotations.Nullable;

public class ChannelManager {

    @Nullable
    public static TextChannel getChannel(Channel channel) {
        String id = channel.getId();
        TextChannel textChannel = NerdBotApp.getBot().getJDA().getTextChannelById(id);
        if (textChannel == null) {
            Logger.error("Failed to find channel: '" + id + "'");
            return null;
        }
        return textChannel;
    }

}
