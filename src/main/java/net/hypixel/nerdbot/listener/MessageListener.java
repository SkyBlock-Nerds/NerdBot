package net.hypixel.nerdbot.listener;

import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.hypixel.nerdbot.channel.Channel;
import net.hypixel.nerdbot.channel.Reactions;

public class MessageListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        Guild guild = event.getGuild();
        net.dv8tion.jda.api.entities.Channel channel = event.getChannel();
        Message message = event.getMessage();
        Emote yes = guild.getEmoteById(Reactions.AGREE.getId());
        Emote no = guild.getEmoteById(Reactions.DISAGREE.getId());

        if (channel.getId().equals(Channel.SUGGESTIONS.getId())) {
            message.addReaction(yes).queue();
            message.addReaction(no).queue();
        }
    }

}
