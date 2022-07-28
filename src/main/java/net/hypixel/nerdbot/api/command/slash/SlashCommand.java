package net.hypixel.nerdbot.api.command.slash;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public interface SlashCommand {

    String getCommandName();

    String getDescription();

    void execute(SlashCommandInteractionEvent event);

}
