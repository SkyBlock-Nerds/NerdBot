package net.hypixel.nerdbot.command;

public class ModCommands {

    /*@Command(name = "curate", permission = "BAN_MEMBERS", permissionMessage = "You do not have permission to use this command.")
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
        message.reply("Curation started at " + new Date()).queue();
        NerdBotApp.EXECUTOR_SERVICE.submit(() -> {
            curator.curate();
            message.addReaction(Reactions.THUMBS_UP_EMOJI).queue();
            message.reply("Curation complete at " + new Date() + "! Took " + curator.getElapsedTime() + "ms").queue();
        });
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
        if (!context.getMessage().getMentionedUsers().isEmpty()) user = context.getMessage().getMentionedUsers().get(0);
        else user = context.getMessage().getJDA().getUserById(args[0]);

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
        int agrees = discordUser.getAgrees().size(), disagrees = discordUser.getDisagrees().size(), total = agrees + disagrees;
        builder.append("\n```");
        builder.append("Total agrees: ").append(discordUser.getAgrees().size()).append("\n");
        builder.append("Total disagrees: ").append(discordUser.getDisagrees().size()).append("\n");
        builder.append("Total suggestion reactions: ").append(total).append("\n");
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

    @Command(name = "removechannelgroup", permission = "BAN_MEMBERS", permissionMessage = "You do not have permission to use this command.")
    public void removeChannelGroup(CommandContext context) {
        String[] args = context.getArgs();
        if (args.length < 1) {
            context.getMessage().reply("Invalid arguments. Usage: `!removechannel <group name>`").queue();
            return;
        }

        ChannelGroup channelGroup = Database.getInstance().getChannelGroup(args[0]);
        if (channelGroup == null) {
            context.getMessage().reply("Channel group `" + args[0] + "` not found!").queue();
            return;
        }

        Database.getInstance().deleteChannelGroup(channelGroup);
        context.getMessage().reply("Removed channel group: `" + channelGroup.getName() + "`").queue();
    }

    @Command(name = "getchannelgroups", permission = "BAN_MEMBERS", permissionMessage = "You do not have permission to use this command.")
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

    @Command(name = "uptime", permission = "BAN_MEMBERS", permissionMessage = "You do not have permission to use this command.")
    public void uptime(CommandContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("**Uptime:** ").append(Time.formatMs(NerdBotApp.getBot().getUptime()));
        context.getMessage().reply(builder.toString()).queue();
    }

    @Command(name = "botinfo", permission = "BAN_MEMBERS", permissionMessage = "You do not have permission to use this command.")
    public void botInfo(CommandContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("**Bot info:**").append("\n");
        SelfUser bot = NerdBotApp.getBot().getJDA().getSelfUser();
        builder.append(" - Bot name: ").append(bot.getName()).append(" (").append(bot.getId()).append(")").append("\n");
        builder.append(" - Bot region: ").append(Region.getRegion()).append("\n");
        builder.append(" - Bot uptime: ").append(Time.formatMs(NerdBotApp.getBot().getUptime())).append("\n");

        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        builder.append(" - Memory: ").append(Util.formatSize(usedMemory)).append(" / ").append(Util.formatSize(totalMemory)).append("\n");
        context.getMessage().reply(builder.toString()).queue();
    }*/

}
