package net.hypixel.nerdbot.api.command.slash;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.List;

public interface SlashCommand {

    String getCommandName();

    String getDescription();

    List<CommandArgument> getArgs();

    void execute(SlashCommandInteractionEvent event);

}
