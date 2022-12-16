package net.hypixel.nerdbot.listener;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.channel.ChannelGroup;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.util.Util;

import java.util.List;

@Log4j2
public class ChannelGroupMessageListener {

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!Database.getInstance().isConnected()
                || !event.isFromGuild()
                || event.getAuthor().isBot() && !event.getAuthor().getId().equals(NerdBotApp.getBot().getJDA().getSelfUser().getId())) {
            return;
        }

        Guild guild = event.getGuild();
        List<ChannelGroup> groups = Database.getInstance().getChannelGroups();
        if (groups == null || groups.isEmpty()) return;

        Emoji yes = guild.getEmojiById(NerdBotApp.getBot().getConfig().getEmojiConfig().getAgreeEmojiId()), no = guild.getEmojiById(NerdBotApp.getBot().getConfig().getEmojiConfig().getDisagreeEmojiId());
        if (yes == null || no == null) {
            log.error("Couldn't find the emote for yes or no!");
            return;
        }

        MessageChannelUnion channel = event.getChannel();
        Message message = event.getMessage();

        for (ChannelGroup group : groups) {
            if (!group.getFrom().equals(channel.getId())) continue;

            message.createThreadChannel("[Discussion] " + Util.getFirstLine(message)).queue(threadChannel -> threadChannel.addThreadMember(message.getAuthor()).queue());

            if (NerdBotApp.getBot().isReadOnly()) {
                log.info("Read-only mode is enabled, not adding reactions to new message " + message.getId());
                return;
            }

            message.addReaction(yes).queue();
            message.addReaction(no).queue();
        }
    }
}
