package net.hypixel.nerdbot.command;

import net.aerh.jdacommands.command.RequiresPermission;
import net.aerh.jdacommands.command.SlashCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.util.Environment;
import net.hypixel.nerdbot.util.Time;
import net.hypixel.nerdbot.util.Util;

public class BotInfoCommand implements SlashCommand, RequiresPermission {

    @Override
    public DefaultMemberPermissions permissionRequired() {
        return DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS);
    }

    @Override
    public String getCommandName() {
        return "botinfo";
    }

    @Override
    public String getDescription() {
        return "Display information about the bot";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        StringBuilder builder = new StringBuilder();
        SelfUser bot = NerdBotApp.getBot().getJDA().getSelfUser();
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();

        builder.append("**Bot info:**").append("\n");
        builder.append(" - Bot name: ").append(bot.getName()).append(" (ID: ").append(bot.getId()).append(")").append("\n");
        builder.append(" - Bot region: ").append(Environment.getEnvironment()).append("\n");
        builder.append(" - Bot uptime: ").append(Time.formatMs(NerdBotApp.getBot().getUptime())).append("\n");
        builder.append(" - Memory: ").append(Util.formatSize(usedMemory)).append(" / ").append(Util.formatSize(totalMemory)).append("\n");

        event.reply(builder.toString()).setEphemeral(true).queue();
    }
}
