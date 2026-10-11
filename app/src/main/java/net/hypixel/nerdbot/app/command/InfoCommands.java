package net.hypixel.nerdbot.app.command;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashComponentHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.hypixel.nerdbot.app.command.util.CommandReplies;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.role.SpecialRoles;
import net.hypixel.nerdbot.app.storage.GreenlitStore;
import net.hypixel.nerdbot.discord.api.bot.Environment;
import net.hypixel.nerdbot.discord.cache.EmojiCache;
import net.hypixel.nerdbot.discord.config.EmojiConfig;
import net.hypixel.nerdbot.discord.config.RoleConfig;
import net.hypixel.nerdbot.discord.util.StringUtils;
import net.hypixel.nerdbot.discord.util.pagination.PaginatedResponse;
import net.hypixel.nerdbot.discord.util.pagination.PaginationManager;
import net.hypixel.nerdbot.marmalade.discord.EmbedFactory;
import net.hypixel.nerdbot.marmalade.format.DiscordTimestamp;
import net.hypixel.nerdbot.marmalade.format.TimeUtils;
import net.hypixel.nerdbot.marmalade.io.FileUtils;
import net.hypixel.nerdbot.marmalade.storage.database.model.greenlit.GreenlitMessage;

import java.lang.management.ManagementFactory;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

@Slf4j
public class InfoCommands {

    static final int GREENLIT_PER_PAGE = 5;

    private final JDA jda;
    private final Provider<NerdBotConfig> config;
    private final GreenlitStore greenlit;

    @Inject
    public InfoCommands(JDA jda, Provider<NerdBotConfig> config, GreenlitStore greenlit) {
        this.jda = jda;
        this.config = config;
        this.greenlit = greenlit;
    }

    @SlashCommand(name = "info", subcommand = "bot", description = "View information about the bot", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void botInfo(SlashCommandInteractionEvent event) {
        showBotInfo(event);
    }

    @SlashCommand(name = "info", subcommand = "greenlit", description = "View greenlit suggestions", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void greenlitInfo(SlashCommandInteractionEvent event) {
        showGreenlit(event);
    }

    @SlashCommand(name = "info", subcommand = "server", description = "View some information about the server", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void serverInfo(SlashCommandInteractionEvent event) {
        showServerInfo(event);
    }

    @SlashComponentHandler(id = "info-pagination", patterns = {"info-page:*"})
    public void handleInfoPagination(ButtonInteractionEvent event) {
        event.deferEdit().queue();

        if (!PaginationManager.handleButtonInteraction(event)) {
            log.warn("Could not find pagination for message ID: {}", event.getMessageId());
            event.getHook().editOriginal("This pagination has expired. Please run the command again.").queue();
        }
    }

    void showBotInfo(IReplyCallback interaction) {
        SelfUser bot = jda.getSelfUser();
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();

        StringBuilder text = new StringBuilder("""
            - Bot name: %s (ID: %s)
            - Branch: `%s`
            - Commit: `%s`
            - Container ID: `%s`
            - Environment: %s
            - Uptime: %s
            - Memory: %s / %s
            """.formatted(
            bot.getName(), bot.getId(),
            FileUtils.getBranchName(),
            FileUtils.getCommitHash(),
            FileUtils.getDockerContainerId(),
            Environment.getEnvironment(),
            TimeUtils.formatMsCompact(ManagementFactory.getRuntimeMXBean().getUptime()),
            StringUtils.formatSize(usedMemory), StringUtils.formatSize(runtime.totalMemory())
        ));

        Map<String, Properties> dependencies = FileUtils.getDependencyGitInfo();
        if (!dependencies.isEmpty()) {
            text.append("\n**Dependencies:**\n");
            dependencies.forEach((name, properties) -> text.append("- %s: `%s` (`%s`)\n".formatted(name,
                properties.getProperty("git.branch", "unknown"),
                properties.getProperty("git.commit.id.abbrev", "unknown"))));
        }

        CommandReplies.replyPrivately(interaction, text.toString());
    }

    void showGreenlit(IReplyCallback interaction) {
        InteractionHook hook = CommandReplies.deferPrivately(interaction);

        CommandReplies.finish(greenlit.loadAll(), hook, "load greenlit suggestions",
            "Failed to load greenlit suggestions. Please try again later.", messages -> {
                if (messages.isEmpty()) {
                    hook.editOriginal("No greenlit suggestions found!").queue();
                    return;
                }

                PaginatedResponse<GreenlitMessage> pagination = PaginatedResponse.forEmbeds(
                    newestFirst(messages), GREENLIT_PER_PAGE, page -> greenlitEmbed(page).build(), "info-page");
                pagination.sendMessage(interaction);
                hook.retrieveOriginal().queue(message -> PaginationManager.registerPagination(message.getId(), pagination));
            });
    }

    void showServerInfo(IReplyCallback interaction) {
        Guild guild = interaction.getGuild();
        if (guild == null) {
            CommandReplies.replyPrivately(interaction, "This command can only be used in a server!");
            return;
        }

        int staff = 0;
        for (String roleName : SpecialRoles.NAMES) {
            Optional<Role> role = guild.getRolesByName(roleName, false).stream().findFirst();
            if (role.isEmpty()) {
                log.warn("Role {} not found", roleName);
            }
            staff += membersWith(guild, role);
        }

        RoleConfig roleConfig = config.get().getRoleConfig();
        int grapes = membersWith(guild, roleById(guild, roleConfig.getModeratorRoleId(), "Grape"));
        int nerds = membersWith(guild, roleById(guild, roleConfig.getOrangeRoleId(), "Orange"));

        CommandReplies.replyPrivately(interaction, """
            Server name: %s (Server ID: %s)
            Created at: %s
            Boosters: %s (%s)
            Channels: %s
            Members: %s/%s
            - Staff: %s
            - Grapes: %s
            - Nerds: %s
            """.formatted(
            guild.getName(), guild.getId(),
            DiscordTimestamp.toRelativeTimestamp(guild.getTimeCreated().toInstant().toEpochMilli()),
            guild.getBoostCount(), guild.getBoostTier().name(),
            guild.getChannels().size(),
            guild.getMembers().size(), guild.getMaxMembers(),
            staff, grapes, nerds));
    }

    static List<GreenlitMessage> newestFirst(List<GreenlitMessage> messages) {
        return messages.stream()
            .sorted(Comparator.comparingLong(GreenlitMessage::getSuggestionTimestamp).reversed())
            .toList();
    }

    private static EmbedBuilder greenlitEmbed(List<GreenlitMessage> messages) {
        EmbedBuilder embed = EmbedFactory.success("Greenlit Suggestions", null);

        for (GreenlitMessage message : messages) {
            String tags = message.getTags().isEmpty() ? "No tags" : String.join(", ", message.getTags());
            String votes = String.format("%s %d | %s %d | %s %d",
                EmojiCache.getFormattedEmoji(EmojiConfig::getAgreeEmojiId), message.getAgrees(),
                EmojiCache.getFormattedEmoji(EmojiConfig::getDisagreeEmojiId), message.getDisagrees(),
                EmojiCache.getFormattedEmoji(EmojiConfig::getNeutralEmojiId), message.getNeutrals());

            embed.addField(StringUtils.truncate(message.getSuggestionTitle(), 100), String.format(
                "%s\n\n**Tags:** %s\n**Votes:** %s\n**Date:** %s\n[View](%s)",
                StringUtils.truncate(message.getSuggestionContent(), 200),
                tags,
                votes,
                DiscordTimestamp.toRelativeTimestamp(message.getSuggestionTimestamp()),
                message.getSuggestionUrl()), false);
        }

        return embed;
    }

    /** An unset or malformed id in the config counts as a missing role rather than an error. */
    private static Optional<Role> roleById(Guild guild, String roleId, String label) {
        Optional<Role> role = Optional.empty();
        if (roleId != null && !roleId.isBlank()) {
            try {
                role = Optional.ofNullable(guild.getRoleById(roleId));
            } catch (NumberFormatException malformed) {
                log.warn("{} role id '{}' in the config is not a valid id", label, roleId);
            }
        }

        if (role.isEmpty()) {
            log.warn("Role {} not found", label);
        }
        return role;
    }

    private static int membersWith(Guild guild, Optional<Role> role) {
        return role.map(found -> guild.getMembersWithRoles(found).size()).orElse(0);
    }
}
