package net.hypixel.nerdbot.listener;

import com.mongodb.client.model.Filters;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.channel.ChannelGroup;
import net.hypixel.nerdbot.api.database.MongoDatabase;
import net.hypixel.nerdbot.util.Util;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public class ChannelGroupMessageListener {
    
    private final MongoDatabase mongoDatabase = NerdBotApp.getBot().getDatabase();

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!mongoDatabase.isConnected()
                || !event.isFromGuild()
                || event.getAuthor().isBot() && !event.getAuthor().getId().equals(NerdBotApp.getBot().getJDA().getSelfUser().getId())) {
            return;
        }

        Guild guild = event.getGuild();
        MessageChannelUnion channel = event.getChannel();
        List<ChannelGroup> groups = mongoDatabase.getCollection("channel_groups", ChannelGroup.class).find(Filters.eq("from", channel.getId())).into(new ArrayList<>());
        if (groups.isEmpty()) {
            return;
        }

        Emoji yes = guild.getEmojiById(NerdBotApp.getBot().getConfig().getEmojiConfig().getAgreeEmojiId()), no = guild.getEmojiById(NerdBotApp.getBot().getConfig().getEmojiConfig().getDisagreeEmojiId());
        if (yes == null || no == null) {
            log.error("Couldn't find the emote for yes or no!");
            return;
        }

        if (NerdBotApp.getBot().isReadOnly()) {
            log.info("Read-only mode is enabled!");
            return;
        }

        Message message = event.getMessage();
        message.createThreadChannel("[Discussion] " + Util.getFirstLine(message)).queue(threadChannel -> threadChannel.addThreadMember(message.getAuthor()).queue());
        message.addReaction(yes).queue();
        message.addReaction(no).queue();
    }
}
