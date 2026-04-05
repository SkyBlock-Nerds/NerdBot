package net.hypixel.nerdbot.app.generation;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.GenerationFeedback;

@UtilityClass
public class DiscordGenerationContext {

    public static GenerationContext fromEvent(SlashCommandInteractionEvent event, boolean defaultEphemeral) {
        GenerationFeedback feedback = (message, forceEphemeral) -> {
            boolean sendEphemeral = forceEphemeral || defaultEphemeral;
            if (event.isAcknowledged()) {
                event.getHook().sendMessage(message).setEphemeral(sendEphemeral).queue();
            } else {
                event.reply(message).setEphemeral(sendEphemeral).queue();
            }
        };

        return GenerationContext.builder()
            .feedback(feedback)
            .build();
    }
}
