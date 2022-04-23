package net.hypixel.nerdbot.command;

import me.neiizun.lightdrop.automapping.AutoMapping;
import me.neiizun.lightdrop.command.Command;
import me.neiizun.lightdrop.command.CommandContext;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.hypixel.nerdbot.channel.Channel;
import net.hypixel.nerdbot.curator.Curator;
import net.hypixel.nerdbot.database.Database;

@AutoMapping
public class ModCommands {

    @Command(name = "curate")
    public void curate(CommandContext context) {
        Member member = context.getMessage().getGuild().getMember(context.getAuthor());

        if (!member.hasPermission(Permission.BAN_MEMBERS)) {
            context.getMessage().reply("You don't have permission to use this command.").queue();
            return;
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
        Curator curator = new Curator(limit, Channel.SUGGESTIONS);
        curator.curate();

        if (!curator.getGreenlitMessages().isEmpty()) {
            curator.applyEmoji();
            curator.insert();
            curator.sendTo(Channel.GREENLIT);
            message.reply("Curation complete. " + curator.getGreenlitMessages().size() + " suggestions were greenlit.").queue();
        } else {
            message.reply("Curation complete. No suggestions were greenlit.").queue();
        }
    }

}
