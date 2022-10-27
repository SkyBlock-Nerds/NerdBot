package net.hypixel.nerdbot.listener;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.channel.ChannelGroup;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.util.Util;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MessageListener {

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() && !event.getAuthor().getId().equals(NerdBotApp.getBot().getJDA().getSelfUser().getId())) {
            return;
        }

        Guild guild = event.getGuild();
        List<ChannelGroup> groups = Database.getInstance().getChannelGroups();
        if (groups == null || groups.isEmpty()) return;

        Emoji yes = guild.getEmojiById(NerdBotApp.getBot().getConfig().getEmojis().getAgree()), no = guild.getEmojiById(NerdBotApp.getBot().getConfig().getEmojis().getDisagree());
        if (yes == null || no == null) {
            NerdBotApp.LOGGER.error("Couldn't find the emote for yes or no!");
            return;
        }

        MessageChannelUnion channel = event.getChannel();
        Message message = event.getMessage();

        for (ChannelGroup group : groups) {
            if (!group.getFrom().equals(channel.getId())) continue;

            message.createThreadChannel("[Discussion] " + Util.getFirstLine(message)).queue(threadChannel -> threadChannel.addThreadMember(message.getAuthor()).queue());

            if (NerdBotApp.getBot().isReadOnly()) {
                NerdBotApp.LOGGER.info("Read-only mode is enabled, not adding reactions to new message " + message.getId());
                return;
            }

            message.addReaction(yes).queue();
            message.addReaction(no).queue();
        }
    }
}
