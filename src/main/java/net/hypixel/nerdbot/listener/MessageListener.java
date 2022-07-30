package net.hypixel.nerdbot.listener;

import net.dv8tion.jda.api.entities.Channel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.channel.ChannelGroup;
import net.hypixel.nerdbot.api.channel.Reactions;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MessageListener implements EventListener {

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        if (event instanceof MessageReceivedEvent messageEvent) {
            if (messageEvent.getAuthor().isBot() && !messageEvent.getAuthor().getId().equals(NerdBotApp.getBot().getJDA().getSelfUser().getId()))
                return;

            Guild guild = messageEvent.getGuild();
            List<ChannelGroup> groups = Database.getInstance().getChannelGroups();
            if (groups == null || groups.isEmpty()) return;

            Emoji yes = guild.getEmojiById(Reactions.AGREE.getId()), no = guild.getEmojiById(Reactions.DISAGREE.getId());
            if (yes == null || no == null) {
                Logger.error("Couldn't find the emote for yes or no!");
                return;
            }

            Channel channel = messageEvent.getChannel();
            Message message = messageEvent.getMessage();

            for (ChannelGroup group : groups) {
                if (!group.getFrom().equals(channel.getId())) continue;

                String firstLine = message.getContentRaw().split("\n")[0];
                if (firstLine == null || firstLine.equals("")) {
                    if (message.getEmbeds().get(0) != null) firstLine = message.getEmbeds().get(0).getTitle();
                    else firstLine = "No Title";
                } else if (firstLine.length() > 30) {
                    firstLine = firstLine.substring(0, 30) + "...";
                }

                message.createThreadChannel("[Discussion] " + firstLine).queue(threadChannel -> threadChannel.addThreadMember(message.getAuthor()).queue());
                message.addReaction(yes).queue();
                message.addReaction(no).queue();
            }
        }
    }

    private String getFirstLine(Message message) {
        String firstLine = message.getContentRaw().split("\n")[0];

        if (firstLine == null || firstLine.equals("")) {
            firstLine = "No Title";

            if (message.getEmbeds().get(0) != null)
                firstLine = message.getEmbeds().get(0).getTitle();
        }

        return firstLine.substring(0, Math.min(30, firstLine.length()));
    }

}
