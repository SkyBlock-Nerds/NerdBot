package net.hypixel.nerdbot.api.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.command.slash.CommandArgument;
import net.hypixel.nerdbot.api.command.slash.SlashCommand;
import net.hypixel.nerdbot.util.Logger;

import java.util.ArrayList;
import java.util.List;

public class CommandManager {

    private final List<SlashCommand> commands = new ArrayList<>();

    private String prefix = "!";

    public CommandManager() {
    }

    public CommandManager(String prefix) {
        this.prefix = prefix;
    }

    public List<SlashCommand> getCommands() {
        return commands;
    }

    public void registerCommand(SlashCommand command) {
        commands.add(command);

        Guild guild = NerdBotApp.getBot().getJDA().getGuildById(NerdBotApp.getBot().getConfig().getGuildId());
        if (guild == null) {
            Logger.error("Couldn't find the guild specified in the bot config!");
            return;
        }

        SlashCommandData data = Commands.slash(command.getCommandName(), command.getDescription());
        if (!command.getArgs().isEmpty()) {
            for (CommandArgument arg : command.getArgs()) {
                data.addOption(arg.optionType(), arg.argument(), arg.description(), arg.required());
            }
        }

        guild.upsertCommand(data).queue();
        guild.updateCommands().complete();
    }

    public void registerCommands(SlashCommand... commands) {
        for (SlashCommand command : commands) {
            registerCommand(command);
        }
    }

    public void unregisterCommand(SlashCommand command) {
        Guild guild = NerdBotApp.getBot().getJDA().getGuildById(NerdBotApp.getBot().getConfig().getGuildId());
        if (guild == null) {
            Logger.error("Couldn't find the guild specified in the bot config!");
            return;
        }

        guild.deleteCommandById(command.getCommandName()).queue();
        commands.remove(command);
    }

    public SlashCommand getCommand(String name) {
        for (SlashCommand command : commands) {
            if (command.getCommandName().equals(name))
                return command;
        }
        return null;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
    
}
