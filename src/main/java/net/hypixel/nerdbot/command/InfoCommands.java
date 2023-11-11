package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.ReactionHistory;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.repository.GreenlitMessageRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.Environment;
import net.hypixel.nerdbot.util.Time;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

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

        builder.append("- Bot name: ").append(bot.getName()).append(" (ID: ").append(bot.getId()).append(")").append("\n")
            .append("- Environment: ").append(Environment.getEnvironment()).append("\n")
            .append("- Uptime: ").append(Time.formatMs(NerdBotApp.getBot().getUptime())).append("\n")
            .append("- Memory: ").append(Util.formatSize(usedMemory)).append(" / ").append(Util.formatSize(totalMemory)).append("\n");

        event.reply(builder.toString()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "info", subcommand = "greenlit", description = "Get a list of all unreviewed greenlit messages. May not be 100% accurate!", defaultLocked = true)
    public void greenlitInfo(GuildSlashEvent event, @AppOption int page, @AppOption @Optional String tag) {
        GreenlitMessageRepository repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(GreenlitMessageRepository.class);

        if (!database.isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            return;
        }

        List<GreenlitMessage> greenlit = repository.getAll();

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
            if (RoleManager.getRole(roleName) == null) {
                log.warn("Role {} not found", roleName);
                continue;
            }
            staff += guild.getMembersWithRoles(RoleManager.getRole(roleName)).size();
        }

        builder.append("Server name: ").append(guild.getName()).append(" (Server ID: ").append(guild.getId()).append(")\n")
            .append("Created at: ").append(new DiscordTimestamp(guild.getTimeCreated().toInstant().toEpochMilli()).toRelativeTimestamp()).append("\n")
            .append("Boosters: ").append(guild.getBoostCount()).append(" (").append(guild.getBoostTier().name()).append(")\n")
            .append("Channels: ").append(guild.getChannels().size()).append("\n")
            .append("Members: ").append(guild.getMembers().size()).append("/").append(guild.getMaxMembers()).append("\n")
            .append("- Staff: ").append(staff).append("\n")
            .append("- Grapes: ").append(guild.getMembersWithRoles(RoleManager.getRole("Grape")).size()).append("\n")
            .append("- Nerds: ").append(guild.getMembersWithRoles(RoleManager.getRole("Nerd")).size());

        event.reply(builder.toString()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "info", subcommand = "activity", description = "View information regarding user activity", defaultLocked = true)
    public void userActivityInfo(GuildSlashEvent event, @AppOption int page) {
        if (!database.isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            return;
        }

        DiscordUserRepository repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        List<DiscordUser> users = getDiscordUsers(repository);
        users.sort(Comparator.comparingLong(discordUser -> discordUser.getLastActivity().getLastGlobalActivity()));

        StringBuilder stringBuilder = new StringBuilder("**Page " + page + "**\n");

        getPage(users, page, 10).forEach(discordUser -> {
            Member member = Util.getMainGuild().getMemberById(discordUser.getDiscordId());
            if (member == null) {
                log.error("Couldn't find member " + discordUser.getDiscordId());
                return;
            }

            stringBuilder.append(" • ").append(member.getUser().getAsMention()).append(" (").append(new DiscordTimestamp(discordUser.getLastActivity().getLastGlobalActivity()).toLongDateTime()).append(")").append("\n");
        });

        event.reply(stringBuilder.toString()).setEphemeral(true).queue();
    }

    @NotNull
    private static List<DiscordUser> getDiscordUsers(DiscordUserRepository repository) {
        List<DiscordUser> users = repository.getAll();

        users.removeIf(discordUser -> {
            Member member = Util.getMainGuild().getMemberById(discordUser.getDiscordId());

            if (member == null) {
                return true;
            }

            if (Arrays.stream(SPECIAL_ROLES).anyMatch(s -> member.getRoles().stream().map(Role::getName).toList().contains(s))) {
                return true;
            }

            return !Instant.ofEpochMilli(discordUser.getLastActivity().getLastGlobalActivity()).isBefore(Instant.now().minus(Duration.ofDays(NerdBotApp.getBot().getConfig().getInactivityDays())));
        });
        return users;
    }

    @JDASlashCommand(name = "info", subcommand = "messages", description = "View an ordered list of users with the most messages", defaultLocked = true)
    public void userMessageInfo(GuildSlashEvent event, @AppOption int page) {
        if (!database.isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            return;
        }

        List<DiscordUser> users = getDiscordUsers(NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class));
        users.sort(Comparator.comparingInt(DiscordUser::getTotalMessageCount));

        StringBuilder stringBuilder = new StringBuilder("**Page " + page + "**\n");

        getPage(users, page, 10).forEach(discordUser -> {
            Member member = Util.getMainGuild().getMemberById(discordUser.getDiscordId());
            if (member == null) {
                log.error("Couldn't find member " + discordUser.getDiscordId());
                return;
            }

            stringBuilder.append(" • ").append(member.getUser().getAsMention()).append(" (").append(Util.COMMA_SEPARATED_FORMAT.format(discordUser.getTotalMessageCount())).append(")").append("\n");
        });

        event.reply(stringBuilder.toString()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "reactions", description = "View a list of recent reactions on suggestions")
    public void showRecentReactions(GuildSlashEvent event, @Optional @AppOption Member member, @Optional @AppOption int page) {
        if (member == null || !event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            member = event.getMember();
        }

        DiscordUserRepository repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = repository.findById(member.getId());

        if (discordUser == null) {
            event.reply("Couldn't find that user!").setEphemeral(true).queue();
            return;
        }

        List<ReactionHistory> reactionHistory = discordUser.getLastActivity().getSuggestionReactionHistory();
        reactionHistory.sort(Comparator.comparingLong(ReactionHistory::suggestionTimestamp).reversed());

        if (reactionHistory.isEmpty()) {
            event.reply("Cannot find any history of your suggestion votes!").setEphemeral(true).queue();
            return;
        }

        Map<String, List<ReactionHistory>> reactionHistoryMap = reactionHistory.stream().collect(Collectors.groupingBy(ReactionHistory::channelId));
        // Show most recent reaction first for each suggestion
        reactionHistoryMap.forEach((s, reactionHistories) -> reactionHistories.sort(Comparator.comparingLong(ReactionHistory::reactionTimestamp).reversed()));

        page = Math.max(1, page);
        StringBuilder stringBuilder = new StringBuilder("**Page " + page + "**\n");
        List<RichCustomEmoji> emojis = Util.getMainGuild().retrieveEmojis().complete();

        getPage(reactionHistoryMap, page, 10).forEach(stringListEntry -> {
            stringBuilder.append("<#").append(stringListEntry.getKey()).append(">\n");
            stringListEntry.getValue().forEach(history -> {
                String emoji = emojis.stream()
                    .filter(e -> e.getName().equals(history.reactionName()))
                    .findFirst()
                    .map(RichCustomEmoji::getAsMention)
                    .orElse(":question:");

                stringBuilder.append(emoji);

                if (history.reactionTimestamp() != -1) {
                    DiscordTimestamp discordTimestamp = new DiscordTimestamp(history.reactionTimestamp());
                    stringBuilder.append(" ").append(discordTimestamp.toShortDateTime()).append(" (").append(discordTimestamp.toRelativeTimestamp()).append(")");
                } else {
                    stringBuilder.append(" (Unknown date/time)");
                }

                stringBuilder.append("\n");
            });

            stringBuilder.append("\n");
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

    /**
     * Returns a view (not a new list) of the source Map for the range based on page and pageSize.
     *
     * @param sourceMap the source map
     * @param page      page number should start from 1
     * @param pageSize  page size
     * @param <K>       the type of keys in the map
     * @param <V>       the type of values in the map
     * @return a list view of map entries for the specified page
     * @throws IllegalArgumentException if the sourceMap is null, page is invalid, or pageSize is non-positive
     */
    public static <K, V> List<Map.Entry<K, V>> getPage(Map<K, V> sourceMap, int page, int pageSize) {
        if (sourceMap == null) {
            throw new IllegalArgumentException("Invalid source map");
        }

        if (page < 1) {
            throw new IllegalArgumentException("Invalid page number: " + page);
        }

        if (pageSize <= 0) {
            throw new IllegalArgumentException("Invalid page size: " + pageSize);
        }

        int fromIndex = (page - 1) * pageSize;
        int toIndex = fromIndex + pageSize;
        List<Map.Entry<K, V>> entries = new ArrayList<>(sourceMap.entrySet());

        if (fromIndex >= entries.size()) {
            return new ArrayList<>();
        }

        toIndex = Math.min(toIndex, entries.size());
        return entries.subList(fromIndex, toIndex);
    }
}
