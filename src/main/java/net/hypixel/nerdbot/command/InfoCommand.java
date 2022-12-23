package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfUser;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.database.user.DiscordUser;
import net.hypixel.nerdbot.api.database.user.LastActivity;
import net.hypixel.nerdbot.util.DiscordTimestamp;
import net.hypixel.nerdbot.util.Environment;
import net.hypixel.nerdbot.util.Time;
import net.hypixel.nerdbot.util.Util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

@Log4j2
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

    @JDASlashCommand(name = "info", subcommand = "greenlit", description = "View some information on greenlit messages", defaultLocked = true)
    public void greenlitInfo(GuildSlashEvent event, @AppOption int page) {
        List<GreenlitMessage> greenlits = getPage(Database.getInstance().getGreenlitCollection(), page, 10);
        greenlits.sort(Comparator.comparingLong(GreenlitMessage::getSuggestionTimestamp));

        StringBuilder stringBuilder = new StringBuilder("**Page " + page + "**\n");
        if (greenlits.isEmpty()) {
            stringBuilder.append("No results found");
        } else {
            for (GreenlitMessage greenlitMessage : greenlits) {
                stringBuilder.append(" • [").append(greenlitMessage.getSuggestionTitle()).append("](").append(greenlitMessage.getSuggestionUrl()).append(")\n");
            }
        }

        event.reply(stringBuilder.toString()).setEphemeral(true).queue();
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
        LastActivity lastActivity = discordUser.getLastActivity();
        builder.append("User stats for ").append(member.getAsMention()).append("\n");
        builder.append(" • Total known agree reactions: ").append(discordUser.getAgrees().size()).append("\n");
        builder.append(" • Total known disagree reactions: ").append(discordUser.getDisagrees().size()).append("\n");
        builder.append(" • Last known activity date: ").append(new DiscordTimestamp(lastActivity.getLastGlobalActivity()).toRelativeTimestamp()).append("\n");
        builder.append(" • Last known activity date (alpha): ").append(new DiscordTimestamp(lastActivity.getLastAlphaActivity()).toRelativeTimestamp()).append("\n");
        builder.append(" • Last known voice channel activity date: ").append(new DiscordTimestamp(lastActivity.getLastVoiceChannelJoinDate()).toRelativeTimestamp()).append("\n");
        builder.append(" • Last known suggestion date: ").append(new DiscordTimestamp(lastActivity.getLastSuggestionDate()).toRelativeTimestamp()).append("\n");

        event.reply(builder.toString()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "info", subcommand = "activity", description = "View information regarding user activity", defaultLocked = true)
    public void userActivityInfo(GuildSlashEvent event, @AppOption int page) {
        if (!Database.getInstance().isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            return;
        }

        List<DiscordUser> users = getPage(Database.getInstance().getUsers().stream().filter(discordUser -> discordUser.getLastActivity().getLastGlobalActivity() == -1L).toList(), page, 15);
        StringBuilder stringBuilder = new StringBuilder("**Page " + page + "**\n");
        for (DiscordUser user : users) {
            Member member = NerdBotApp.getBot().getJDA().getGuildById(NerdBotApp.getBot().getConfig().getGuildId()).getMemberById(user.getDiscordId());
            if (member == null) {
                log.error("Couldn't find member " + user.getDiscordId());
                continue;
            }

            if (member.getRoles().contains(Util.getRole("Ultimate Nerd")) || member.getRoles().contains(Util.getRole("Ultimate Nerd But Red")) ||
                    member.getRoles().contains(Util.getRole("Game Master"))) {
                log.info("Would have added " + member.getEffectiveName() + " as an inactive user but they have a special role!");
                continue;
            }

            stringBuilder.append(" • ").append(member.getUser().getAsMention()).append("\n");
        }

        event.reply(stringBuilder.toString()).setEphemeral(true).queue();
    }

    private String getDateString(long timestamp) {
        if (timestamp == -1) {
            return "N/A";
        }

        return Time.GLOBAL_DATE_TIME_FORMAT.format(new Date(timestamp));
    }

    /**
     * returns a view (not a new list) of the sourceList for the
     * range based on page and pageSize
     *
     * @param sourceList
     * @param page       page number should start from 1
     * @param pageSize
     *
     * @return custom error can be given instead of returning emptyList
     */
    public static <T> List<T> getPage(List<T> sourceList, int page, int pageSize) {
        if (pageSize <= 0 || page <= 0) {
            throw new IllegalArgumentException("Invalid page: " + pageSize);
        }

        int fromIndex = (page - 1) * pageSize;
        if (sourceList == null || sourceList.size() <= fromIndex) {
            return new ArrayList<>();
        }

        return sourceList.subList(fromIndex, Math.min(fromIndex + pageSize, sourceList.size()));
    }
}
