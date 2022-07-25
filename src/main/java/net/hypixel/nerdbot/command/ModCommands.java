package net.hypixel.nerdbot.command;

import me.neiizun.lightdrop.automapping.AutoMapping;
import me.neiizun.lightdrop.command.Command;
import me.neiizun.lightdrop.command.CommandContext;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.channel.ChannelGroup;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.DiscordUser;
import net.hypixel.nerdbot.curator.Curator;
import net.hypixel.nerdbot.util.Region;
import net.hypixel.nerdbot.util.Util;

@AutoMapping
public class ModCommands {

    @Command(name = "curate", permission = "BAN_MEMBERS", permissionMessage = "You do not have permission to use this command.")
    public void curate(CommandContext context) {
        if (!Database.getInstance().isConnected()) {
            context.getMessage().reply("Cannot connect to the database!").queue();
            return;
        }

        String[] args = context.getArgs();
        int limit = 100;
        ChannelGroup channelGroup = Database.getInstance().getChannelGroup("DefaultSuggestions");
        if (args.length > 0) {
            try {
                limit = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                context.getMessage().reply("Invalid limit: " + args[0]).queue();
            }

            if (args[1] != null) {
                channelGroup = Database.getInstance().getChannelGroup(args[1]);
            }
        }

        Message message = context.getMessage();
        Curator curator = new Curator(limit, channelGroup);
        curator.curate();

        if (!curator.getGreenlitMessages().isEmpty()) {
            curator.applyEmoji();
            curator.insertIntoDatabase();
            curator.sendGreenlitToChannel();
            message.reply("Curation complete. " + curator.getGreenlitMessages().size() + " suggestion" + (curator.getGreenlitMessages().size() == 1 ? " was" : "s were") + " greenlit.").queue();
        } else {
            message.reply("Curation complete. No suggestions were greenlit.").queue();
        }
    }

    @Command(name = "userstats", permission = "BAN_MEMBERS", permissionMessage = "You do not have permission to use this command.")
    public void getUserStats(CommandContext context) {
        if (!Database.getInstance().isConnected()) {
            context.getMessage().reply("Cannot connect to the database!").queue();
            return;
        }

        String[] args = context.getArgs();
        if (args.length == 0) {
            context.getMessage().reply("Please specify a user!").queue();
            return;
        }

        User user;
        if (!context.getMessage().getMentionedUsers().isEmpty())
            user = context.getMessage().getMentionedUsers().get(0);
        else
            user = context.getMessage().getJDA().getUserById(args[0]);
        if (user == null) {
            context.getMessage().reply("Cannot find that user!").queue();
            return;
        }

        DiscordUser discordUser = Database.getInstance().getUser(user.getId());
        if (discordUser == null) {
            context.getMessage().reply("User `" + user.getAsTag() + "` not found in database!").queue();
            return;
        }

        StringBuilder builder = new StringBuilder("**User stats for " + user.getAsTag() + ":**");
        builder.append("\n```");
        builder.append("Total agrees: ").append(discordUser.getTotalAgrees()).append("\n");
        builder.append("Total disagrees: ").append(discordUser.getTotalDisagrees()).append("\n");
        builder.append("Total suggestion reactions: ").append(discordUser.getTotalSuggestionReactions()).append("\n");
        builder.append("Last recorded activity date: ").append(discordUser.getLastKnownActivityDate()).append("```");
        context.getMessage().reply(builder.toString()).queue();
    }

    @Command(name = "addchannelgroup", permission = "BAN_MEMBERS", permissionMessage = "You do not have permission to use this command.")
    public void addChannelGroup(CommandContext context) {
        String[] args = context.getArgs();
        if (args.length < 3) {
            context.getMessage().reply("Invalid arguments. Usage: `!addchannel <group name> <from> <to>`").queue();
            return;
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

    @Command(name = "uptime")
    public void uptime(CommandContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("**Uptime:** ").append(Util.formatMs(NerdBotApp.getBot().getUptime()));
        context.getMessage().reply(builder.toString()).queue();
    }

    @Command(name = "botinfo")
    public void botInfo(CommandContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("**Bot info:**").append("\n");
        SelfUser bot = NerdBotApp.getBot().getJDA().getSelfUser();
        builder.append(" - Bot name: ").append(bot.getName()).append(" (").append(bot.getId()).append(")").append("\n");
        builder.append(" - Bot region: ").append(Region.getRegion()).append("\n");
        builder.append(" - Bot uptime: ").append(Util.formatMs(NerdBotApp.getBot().getUptime()));
        context.getMessage().reply(builder.toString()).queue();
    }

}
