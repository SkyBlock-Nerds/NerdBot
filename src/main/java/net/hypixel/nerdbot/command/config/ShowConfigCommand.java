package net.hypixel.nerdbot.command.config;

import net.aerh.jdacommands.command.RequiresPermission;
import net.aerh.jdacommands.command.SlashCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.hypixel.nerdbot.NerdBotApp;

public class ShowConfigCommand implements SlashCommand, RequiresPermission {

    @Override
    public DefaultMemberPermissions permissionRequired() {
        return DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR);
    }

    @Override
    public String getCommandName() {
        return "showconfig";
    }

    @Override
    public String getDescription() {
        return "Show the current config values for the bot";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.reply("```json\n" + NerdBotApp.getBot().getConfig().toString() + "```").setEphemeral(true).queue();
    }
}
