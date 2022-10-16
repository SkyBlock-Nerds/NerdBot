package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import net.dv8tion.jda.api.entities.*;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.DiscordUser;
import net.hypixel.nerdbot.util.Environment;
import net.hypixel.nerdbot.util.Time;
import net.hypixel.nerdbot.util.Util;

public class InfoCommand extends ApplicationCommand {

    @JDASlashCommand(name = "info", subcommand = "bot", description = "View information about the bot", defaultLocked = true)
    public void botInfo(GuildSlashEvent event) {
        StringBuilder builder = new StringBuilder();
        SelfUser bot = NerdBotApp.getBot().getJDA().getSelfUser();
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();

        builder.append(" • Bot name: ").append(bot.getName()).append(" (ID: ").append(bot.getId()).append(")").append("\n")
                .append("• Environment: ").append(Environment.getEnvironment()).append("\n")
                .append("• Uptime: ").append(Time.formatMs(NerdBotApp.getBot().getUptime())).append("\n")
                .append("• Memory: ").append(Util.formatSize(usedMemory)).append(" / ").append(Util.formatSize(totalMemory)).append("\n");

        event.reply(builder.toString()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "info", subcommand = "greenlit", description = "View the amount of greenlit messages", defaultLocked = true)
    public void greenlitInfo(GuildSlashEvent event) {
        int amount = Database.getInstance().getGreenlitCollection().size();
        event.reply("There are currently " + amount + " greenlit message" + (amount == 1 ? "" : "s")).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "info", subcommand = "server", description = "View some information about the server", defaultLocked = true)
    public void serverInfo(GuildSlashEvent event) {
        Guild guild = event.getGuild();
        StringBuilder builder = new StringBuilder();
        int staff = guild.getMembersWithRoles(Util.getRole("Ultimate Nerd")).size()
                + guild.getMembersWithRoles(Util.getRole("Ultimate Nerd But Red")).size()
                + guild.getMembersWithRoles(Util.getRole("Game Master")).size();

        builder.append("Server name: ").append(guild.getName()).append(" (Server ID: ").append(guild.getId()).append(")\n")
                .append("Boosters: ").append(guild.getBoostCount()).append(" (").append(guild.getBoostTier().name()).append(")\n")
                .append("Channels: ").append(guild.getChannels().size()).append("\n")
                .append("Members: ").append(guild.getMembers().size()).append("/").append(guild.getMaxMembers()).append("\n")
                .append("  • Staff: ").append(staff).append("\n")
                .append("  • HPC: ").append(guild.getMembersWithRoles(Util.getRole("HPC")).size()).append("\n")
                .append("  • Grapes: ").append(guild.getMembersWithRoles(Util.getRole("Grape")).size()).append("\n")
                .append("  • Nerds: ").append(guild.getMembersWithRoles(Util.getRole("Nerd")).size());

        event.reply(builder.toString()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "info", subcommand = "user", description = "View some information about a user", defaultLocked = true)
    public void userInfo(GuildSlashEvent event, @AppOption(description = "The user to search") Member member) {
        if (!Database.getInstance().isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            return;
        }

        DiscordUser discordUser = Database.getInstance().getUser(member.getId());
        if (discordUser == null) {
            event.reply("Couldn't find that user in the database!").setEphemeral(true).queue();
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("User stats for ").append(member.getAsMention()).append("\n");
        builder.append(" • Total known agree reactions: ").append(discordUser.getAgrees().size()).append("\n");
        builder.append(" • Total known disagree reactions: ").append(discordUser.getDisagrees().size()).append("\n");
        builder.append(" • Last known activity date: ").append(discordUser.getLastKnownActivityDate() == null ? "N/A" : discordUser.getLastKnownActivityDate());

        event.reply(builder.toString()).setEphemeral(true).queue();
    }
}
