package net.hypixel.nerdbot.discord.util;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.hypixel.nerdbot.generator.GenerationContext;
import net.hypixel.nerdbot.generator.GenerationFeedback;

public final class DiscordGenerationContext {

    private DiscordGenerationContext() {
    }

    public static GenerationContext fromEvent(SlashCommandInteractionEvent event, boolean defaultEphemeral) {
        GenerationFeedback feedback = (message, forceEphemeral) -> {
            boolean sendEphemeral = forceEphemeral || defaultEphemeral;
            if (event.isAcknowledged()) {
                event.getHook().sendMessage(message).setEphemeral(sendEphemeral).queue();
            } else {
                event.reply(message).setEphemeral(sendEphemeral).queue();
            }
        };

        String channelId = event.getChannel() != null ? event.getChannel().getId() : null;
        return new GenerationContext(channelId, feedback);
    }
}