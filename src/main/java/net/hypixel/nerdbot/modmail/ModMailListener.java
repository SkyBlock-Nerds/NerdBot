package net.hypixel.nerdbot.modmail;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;

@Slf4j
public class ModMailListener {

    @SubscribeEvent
    public void onModMailReceived(MessageReceivedEvent event) {
        User author = event.getAuthor();
        if (author.isBot() || author.isSystem()) {
            return;
        }

        if (event.getChannelType() != ChannelType.PRIVATE) {
            return;
        }

        ModMailService modMailService = ModMailService.getInstance();
        if (modMailService.getModMailChannel().isEmpty()) {
            return;
        }

        Message message = event.getMessage();
        modMailService.handleIncomingMessage(author, message.getContentDisplay(), message.getAttachments());
    }

    @SubscribeEvent
    public void onModMailResponse(MessageReceivedEvent event) {
        User author = event.getAuthor();
        if (author.isBot() || author.isSystem()) {
            return;
        }

        if (event.getChannelType() != ChannelType.GUILD_PUBLIC_THREAD) {
            return;
        }

        ThreadChannel threadChannel = event.getChannel().asThreadChannel();
        ModMailService modMailService = ModMailService.getInstance();

        if (!modMailService.isModMailThread(threadChannel)) {
            return;
        }

        Message message = event.getMessage();
        modMailService.handleStaffResponse(author, threadChannel, message.getContentDisplay(), message.getAttachments());
    }
}
