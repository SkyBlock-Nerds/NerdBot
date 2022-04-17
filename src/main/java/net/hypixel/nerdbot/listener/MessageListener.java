package net.hypixel.nerdbot.listener;

import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.hypixel.nerdbot.channel.Channel;

public class MessageListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.getChannel().getId().equals(Channel.SUGGESTIONS.getId())) {
            return;
        }
        Guild guild = event.getGuild();
        Emote yes = guild.getEmotesByName("yes", true).stream().findFirst().orElse(null);
        Emote no = guild.getEmotesByName("no", true).stream().findFirst().orElse(null);

        event.getMessage().addReaction(yes).queue();
        event.getMessage().addReaction(no).queue();
    }
}
