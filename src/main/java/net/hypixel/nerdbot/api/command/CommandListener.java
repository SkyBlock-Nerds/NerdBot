package net.hypixel.nerdbot.api.command;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.command.slash.SlashCommand;
import net.hypixel.nerdbot.util.Logger;
import org.jetbrains.annotations.NotNull;

public class CommandListener implements EventListener {

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        if (event instanceof SlashCommandInteractionEvent slashCommandEvent) {
            SlashCommand command = NerdBotApp.getBot().getCommands().getCommand(slashCommandEvent.getName());
            if (command == null) {
                Logger.info("Command " + slashCommandEvent.getName() + " not found!");
                return;
            }

            command.execute(slashCommandEvent);
            Logger.info(slashCommandEvent.getUser().getAsTag() + " used command " + slashCommandEvent.getName());
        }
    }

}
