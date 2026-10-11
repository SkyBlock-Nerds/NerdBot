package net.hypixel.nerdbot.discord.commandfixture;

import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class PingCommands {

    @SlashCommand(name = "ping", description = "Replies with pong")
    public void ping(SlashCommandInteractionEvent event) {
        event.reply("pong").queue();
    }
}
