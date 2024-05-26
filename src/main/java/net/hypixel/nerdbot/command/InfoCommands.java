package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.components.Components;
import com.freya02.botcommands.api.components.InteractionConstraints;
import com.freya02.botcommands.api.components.annotations.JDASelectionMenuListener;
import com.freya02.botcommands.api.components.builder.selects.PersistentStringSelectionMenuBuilder;
import com.freya02.botcommands.api.components.event.StringSelectionEvent;
import com.freya02.botcommands.api.pagination.paginator.Paginator;
import com.freya02.botcommands.api.pagination.paginator.PaginatorBuilder;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.bot.Environment;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.repository.GreenlitMessageRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.TimeUtil;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Log4j2
public class InfoCommands extends ApplicationCommand {

    private static final String GREENLIT_SELECTION_MENU_HANDLER_NAME = "greenlit";
    private static final int MAX_ENTRIES_PER_PAGE = 25;

    private final Database database = NerdBotApp.getBot().getDatabase();

    @NotNull
    private static List<DiscordUser> getDiscordUsers(DiscordUserRepository repository) {
        List<DiscordUser> users = new ArrayList<>(repository.getAll());

        log.info("Checking " + users.size() + " users");

        Iterator<DiscordUser> iterator = users.iterator();
        while (iterator.hasNext()) {
            DiscordUser user = iterator.next();

            user.getMember().ifPresent(member -> {
                if (member.getUser().isBot() || Arrays.stream(Util.SPECIAL_ROLES).anyMatch(s -> member.getRoles().stream().map(Role::getName).toList().contains(s))) {
                    iterator.remove();
                    log.debug("Removed " + user.getDiscordId() + " from the list of users because they are a bot or have a special role");
                }
            });
        }

        return users;
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
            return new ArrayList<>();
        }

        return sourceList.subList(fromIndex, Math.min(fromIndex + pageSize, sourceList.size()));
    }

    @JDASlashCommand(name = "info", subcommand = "bot", description = "View information about the bot", defaultLocked = true)
    public void botInfo(GuildSlashEvent event) {
        StringBuilder builder = new StringBuilder();
        SelfUser bot = NerdBotApp.getBot().getJDA().getSelfUser();
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();

        builder.append("- Bot name: ").append(bot.getName()).append(" (ID: ").append(bot.getId()).append(")").append("\n")
            .append("- Environment: ").append(Environment.getEnvironment()).append("\n")
            .append("- Uptime: ").append(TimeUtil.formatMsCompact(NerdBotApp.getBot().getUptime())).append("\n")
            .append("- Memory: ").append(Util.formatSize(usedMemory)).append(" / ").append(Util.formatSize(totalMemory)).append("\n");

        event.reply(builder.toString()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "info", subcommand = "greenlit", description = "Get a list of all unreviewed greenlit messages. May not be 100% accurate!", defaultLocked = true)
    public void greenlitInfo(GuildSlashEvent event) {
        if (!database.isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            return;
        }

        GreenlitMessageRepository repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(GreenlitMessageRepository.class);
        List<EmbedBuilder> embeds = new ArrayList<>();

        if (repository.isEmpty()) {
            event.reply("There are no greenlit messages!").setEphemeral(true).queue();
            return;
        }

        repository.getAll().forEach(greenlitMessage -> embeds.add(greenlitMessage.createEmbed()));

        Paginator paginator = new PaginatorBuilder()
            .setConstraints(InteractionConstraints.ofUsers(event.getUser()))
            .setPaginatorSupplier((instance, editBuilder, components, page) -> {
                PersistentStringSelectionMenuBuilder builder = Components.stringSelectionMenu(GREENLIT_SELECTION_MENU_HANDLER_NAME);
                int lowerBound = page == 0 ? 0 : MAX_ENTRIES_PER_PAGE * page;
                int upperBound = MAX_ENTRIES_PER_PAGE + (page * MAX_ENTRIES_PER_PAGE);

                for (int i = lowerBound; i < upperBound; i++) {
                    if (i >= embeds.size()) {
                        break;
                    }

                    GreenlitMessage greenlitMessage = repository.getByIndex(i);
                    String description = greenlitMessage.getSuggestionContent().length() > 100 ? greenlitMessage.getSuggestionContent().substring(0, 96) + "..." : greenlitMessage.getSuggestionContent();
                    builder.addOption(greenlitMessage.getSuggestionTitle(), greenlitMessage.getMessageId(), description);
                }

                components.addComponents(builder.build());
                return embeds.get(lowerBound).build();
            })
            .setMaxPages(embeds.size() / MAX_ENTRIES_PER_PAGE)
            .build();

        event.reply(MessageCreateData.fromEditData(paginator.get()))
            .setEphemeral(true)
            .queue();
    }

    @JDASelectionMenuListener(name = GREENLIT_SELECTION_MENU_HANDLER_NAME)
    public void run(StringSelectionEvent event) {
        GreenlitMessageRepository repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(GreenlitMessageRepository.class);
        GreenlitMessage greenlitMessage = repository.findById(event.getValues().get(0));

        event.editMessageEmbeds(greenlitMessage.createEmbed().build()).queue();
    }

    @JDASlashCommand(name = "info", subcommand = "server", description = "View some information about the server", defaultLocked = true)
    public void serverInfo(GuildSlashEvent event) {
        Guild guild = event.getGuild();
        StringBuilder builder = new StringBuilder();

        AtomicInteger staff = new AtomicInteger();
        AtomicInteger grapes = new AtomicInteger();
        AtomicInteger nerds = new AtomicInteger();
        for (String roleName : Util.SPECIAL_ROLES) {
            RoleManager.getRole(roleName).ifPresentOrElse(role -> staff.addAndGet(guild.getMembersWithRoles(role).size()),
                () -> log.warn("Role {} not found", roleName)
            );
        }

        builder.append("Server name: ").append(guild.getName()).append(" (Server ID: ").append(guild.getId()).append(")\n")
            .append("Created at: ").append(DiscordTimestamp.toRelativeTimestamp(guild.getTimeCreated().toInstant().toEpochMilli())).append("\n")
            .append("Boosters: ").append(guild.getBoostCount()).append(" (").append(guild.getBoostTier().name()).append(")\n")
            .append("Channels: ").append(guild.getChannels().size()).append("\n")
            .append("Members: ").append(guild.getMembers().size()).append("/").append(guild.getMaxMembers()).append("\n")
            .append("- Staff: ").append(staff.get()).append("\n");

        RoleManager.getRole("Grape").ifPresentOrElse(role -> grapes.set(guild.getMembersWithRoles(role).size()),
            () -> log.warn("Role {} not found", "Grape"));

        RoleManager.getRole("Nerd").ifPresentOrElse(role -> nerds.set(guild.getMembersWithRoles(role).size()),
            () -> log.warn("Role {} not found", "Nerd"));

        builder.append("- Grapes: ").append(grapes.get()).append("\n")
            .append("- Nerds: ").append(nerds.get());

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
            discordUser.getMember().ifPresentOrElse(member -> {
                stringBuilder.append(" • ")
                    .append(member.getAsMention())
                    .append(" (")
                    .append(DiscordTimestamp.toLongDateTime(discordUser.getLastActivity().getLastGlobalActivity()))
                    .append(")")
                    .append("\n");
            }, () -> log.error("Couldn't find member " + discordUser.getDiscordId()));
        });

        event.reply(stringBuilder.toString()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "info", subcommand = "messages", description = "View an ordered list of users with the most messages", defaultLocked = true)
    public void userMessageInfo(GuildSlashEvent event, @AppOption int page) {
        if (!database.isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            return;
        }

        List<DiscordUser> users = getDiscordUsers(NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class));
        users.sort(Comparator.comparingInt(value -> value.getLastActivity().getTotalMessageCount()));

        StringBuilder stringBuilder = new StringBuilder("**Page " + page + "**\n");

        getPage(users, page, 10).forEach(discordUser -> {
            discordUser.getMember().ifPresentOrElse(member -> {
                stringBuilder.append(" • ").append(member.getAsMention()).append(" (").append(Util.COMMA_SEPARATED_FORMAT.format(discordUser.getLastActivity().getTotalMessageCount())).append(")").append("\n");
            }, () -> log.error("Couldn't find member " + discordUser.getDiscordId()));
        });

        event.reply(stringBuilder.toString()).setEphemeral(true).queue();
    }
}
