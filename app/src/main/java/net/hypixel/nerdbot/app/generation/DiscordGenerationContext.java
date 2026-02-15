package net.hypixel.nerdbot.app.generation;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.hypixel.nerdbot.discord.config.channel.ChannelConfig;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.context.GenerationFeedback;
import net.hypixel.nerdbot.marmalade.format.TimeUtils;

import java.util.Arrays;

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

        String channelId = event.getChannel().getId();

        // Determine if April Fools mode should be enabled for this channel
        boolean aprilFools = false;
        if (TimeUtils.isAprilFirst()) {
            ChannelConfig channelConfig = DiscordBotEnvironment.getBot().getConfig().getChannelConfig();
            aprilFools = Arrays.stream(channelConfig.getFilteredAprilFoolsGenChannelIds())
                .anyMatch(id -> id.equalsIgnoreCase(channelId));
        }

        return new GenerationContext(channelId, feedback, aprilFools);
    }
}
