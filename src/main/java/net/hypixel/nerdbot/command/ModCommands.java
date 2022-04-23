package net.hypixel.nerdbot.command;

import me.neiizun.lightdrop.automapping.AutoMapping;
import me.neiizun.lightdrop.command.Command;
import me.neiizun.lightdrop.command.CommandContext;
import net.dv8tion.jda.api.entities.Message;
import net.hypixel.nerdbot.channel.ChannelGroup;
import net.hypixel.nerdbot.curator.Curator;
import net.hypixel.nerdbot.database.Database;
import net.hypixel.nerdbot.util.Util;

@AutoMapping
public class ModCommands {

    @Command(name = "curate")
    public void curate(CommandContext context) {
        if (!Util.isMod(context.getAuthor().getId(), context.getMessage().getGuild().getId())) {
            context.getChannel().sendMessage("You do not have permission to use this command.").queue();
        }

        if (!Database.getInstance().isConnected()) {
            context.getMessage().reply("Cannot connect to the database!").queue();
        }

        String[] args = context.getArgs();
        int limit = 100;

        if (args.length > 0) {
            try {
                limit = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                context.getMessage().reply("Invalid limit: " + args[0]).queue();
            }
        }

        Message message = context.getMessage();
        Curator curator = new Curator(limit, Database.getInstance().getChannelGroup("DefaultSuggestions"));
        curator.curate();

        if (!curator.getGreenlitMessages().isEmpty()) {
            curator.applyEmoji();
            curator.insert();
            curator.send();
            message.reply("Curation complete. " + curator.getGreenlitMessages().size() + " suggestions were greenlit.").queue();
        } else {
            message.reply("Curation complete. No suggestions were greenlit.").queue();
        }
    }

    @Command(name = "addchannel")
    public void addChannelGroup(CommandContext context) {
        if (!Util.isMod(context.getAuthor().getId(), context.getMessage().getGuild().getId())) {
            context.getChannel().sendMessage("You do not have permission to use this command.").queue();
        }

        String[] args = context.getArgs();

        if (args.length < 3) {
            context.getMessage().reply("Invalid arguments. Usage: `!addchannel <group name> <from> <to>`").queue();
        }

        ChannelGroup channelGroup = new ChannelGroup(args[0], context.getMessage().getGuild().getId(), args[1], args[2]);
        Database.getInstance().insertChannelGroup(channelGroup);
        context.getMessage().reply("Added channel group: `" + channelGroup.getName() + "`").queue();
    }

    @Command(name = "getchannelgroups")
    public void getGroups(CommandContext context) {
        StringBuilder builder = new StringBuilder();

        builder.append("**Channel Groups:**").append("\n");

        Database.getInstance().getChannelGroups().forEach(group ->
                builder.append(" - ")
                        .append(group.getName())
                        .append(" (from: ").append(group.getFrom()).append(", to: ").append(group.getTo()).append(")")
                        .append("\n"));

        context.getMessage().reply(builder.toString()).queue();
    }

}
