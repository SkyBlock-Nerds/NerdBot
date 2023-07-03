package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.util.Environment;
import net.hypixel.nerdbot.util.Time;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Log4j2
public class InfoCommands extends ApplicationCommand {

    private static final String[] SPECIAL_ROLES = {"Ultimate Nerd", "Ultimate Nerd But Red", "Game Master"};

    private final Database database = NerdBotApp.getBot().getDatabase();

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

    @JDASlashCommand(name = "info", subcommand = "greenlit", description = "Get a list of all non-docced greenlit messages. May not be 100% accurate!", defaultLocked = true)
    public void greenlitInfo(GuildSlashEvent event, @AppOption int page, @AppOption @Optional String tag) {
        List<GreenlitMessage> greenlit = database.getCollection("greenlit_messages", GreenlitMessage.class)
            .find()
            .into(new ArrayList<>())
            .stream()
            .filter(greenlitMessage -> greenlitMessage.getTags() != null && !greenlitMessage.getTags().contains("Docced"))
            .toList();

        if (tag != null) {
            greenlit = greenlit.stream().filter(greenlitMessage -> greenlitMessage.getTags().contains(tag)).toList();
        }

        List<GreenlitMessage> pages = getPage(greenlit, page, 10);
        StringBuilder stringBuilder = new StringBuilder("**Page " + page + "**\n");
        if (pages.isEmpty()) {
            stringBuilder.append("No results found");
        } else {
            for (GreenlitMessage greenlitMessage : pages) {
                stringBuilder.append(" • [").append(greenlitMessage.getSuggestionTitle()).append("](").append(greenlitMessage.getSuggestionUrl()).append(")\n");
            }
        }

        event.reply(stringBuilder.toString()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "info", subcommand = "server", description = "View some information about the server", defaultLocked = true)
    public void serverInfo(GuildSlashEvent event) {
        Guild guild = event.getGuild();
        StringBuilder builder = new StringBuilder();

        int staff = 0;
        for (String roleName : SPECIAL_ROLES) {
            if (Util.getRole(roleName) == null) {
                log.warn("Role {} not found", roleName);
                continue;
            }
            staff += guild.getMembersWithRoles(Util.getRole(roleName)).size();
        }

        builder.append("Server name: ").append(guild.getName()).append(" (Server ID: ").append(guild.getId()).append(")\n")
            .append("Created at: ").append(new DiscordTimestamp(guild.getTimeCreated().toInstant().toEpochMilli()).toRelativeTimestamp()).append("\n")
            .append("Boosters: ").append(guild.getBoostCount()).append(" (").append(guild.getBoostTier().name()).append(")\n")
            .append("Channels: ").append(guild.getChannels().size()).append("\n")
            .append("Members: ").append(guild.getMembers().size()).append("/").append(guild.getMaxMembers()).append("\n")
            .append("  • Staff: ").append(staff).append("\n")
            .append("  • HPC: ").append(guild.getMembersWithRoles(Util.getRole("HPC")).size()).append("\n")
            .append("  • Grapes: ").append(guild.getMembersWithRoles(Util.getRole("Grape")).size()).append("\n")
            .append("  • Nerds: ").append(guild.getMembersWithRoles(Util.getRole("Nerd")).size());

        event.reply(builder.toString()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "info", subcommand = "user", description = "View information about a user", defaultLocked = true)
    public void userInfo(GuildSlashEvent event, @AppOption(description = "The user to search") Member member) {
        if (!database.isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            return;
        }

        DiscordUser discordUser;
        if (NerdBotApp.USER_CACHE.getIfPresent(member.getId()) != null) {
            discordUser = NerdBotApp.USER_CACHE.getIfPresent(member.getId());
        } else {
            discordUser = database.findDocument(database.getCollection("users", DiscordUser.class), "discordId", member.getId()).first();
        }

        if (discordUser == null) {
            event.reply("Couldn't find that user in the database!").setEphemeral(true).queue();
            return;
        }

        Pair<EmbedBuilder, EmbedBuilder> activityEmbeds = UserCommands.getActivityEmbeds(event.getMember());

        if (activityEmbeds.getLeft() == null || activityEmbeds.getRight() == null) {
            event.reply("Couldn't find that user in the database!").setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(activityEmbeds.getLeft().build(), activityEmbeds.getRight().build())
            .setEphemeral(true)
            .queue();
    }

    @JDASlashCommand(name = "info", subcommand = "activity", description = "View information regarding user activity", defaultLocked = true)
    public void userActivityInfo(GuildSlashEvent event, @AppOption int page) {
        if (!database.isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            return;
        }

        List<DiscordUser> users = database.getCollection("users", DiscordUser.class).find().into(new ArrayList<>());
        users.removeIf(discordUser -> {
            Member member = NerdBotApp.getBot().getJDA().getGuildById(NerdBotApp.getBot().getConfig().getGuildId()).getMemberById(discordUser.getDiscordId());

            if (member == null) {
                return true;
            }

            if (Arrays.stream(SPECIAL_ROLES).anyMatch(s -> member.getRoles().stream().map(Role::getName).toList().contains(s))) {
                return true;
            }

            return !Instant.ofEpochMilli(discordUser.getLastActivity().getLastGlobalActivity()).isBefore(Instant.now().minus(Duration.ofDays(NerdBotApp.getBot().getConfig().getInactivityDays())));
        });

        log.info("Found " + users.size() + " inactive user" + (users.size() == 1 ? "" : "s") + "!");
        users.sort(Comparator.comparingLong(discordUser -> discordUser.getLastActivity().getLastGlobalActivity()));
        StringBuilder stringBuilder = new StringBuilder("**Page " + page + "**\n");

        getPage(users, page, 10).forEach(discordUser -> {
            Member member = NerdBotApp.getBot().getJDA().getGuildById(NerdBotApp.getBot().getConfig().getGuildId()).getMemberById(discordUser.getDiscordId());
            if (member == null) {
                log.error("Couldn't find member " + discordUser.getDiscordId());
                return;
            }

            stringBuilder.append(" • ").append(member.getUser().getAsMention()).append(" (").append(new DiscordTimestamp(discordUser.getLastActivity().getLastGlobalActivity()).toLongDateTime()).append(")").append("\n");
        });

        event.reply(stringBuilder.toString()).setEphemeral(true).queue();
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
        if (sourceList == null) {
            throw new IllegalArgumentException("Invalid source list");
        }

        if (pageSize <= 0) {
            throw new IllegalArgumentException("Invalid page size: " + pageSize);
        }

        page = Math.max(page, 1);
        int fromIndex = (page - 1) * pageSize;

        if (sourceList.size() <= fromIndex) {
            return getPage(sourceList, page - 1, pageSize); // Revert to last page
        }

        return sourceList.subList(fromIndex, Math.min(fromIndex + pageSize, sourceList.size()));
    }
}
