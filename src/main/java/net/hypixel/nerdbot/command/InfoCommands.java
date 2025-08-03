package net.hypixel.nerdbot.command;

import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.bot.Environment;
import net.hypixel.nerdbot.bot.config.RoleConfig;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.FileUtils;
import net.hypixel.nerdbot.util.StringUtils;
import net.hypixel.nerdbot.util.TimeUtils;
import net.hypixel.nerdbot.util.Utils;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class InfoCommands {

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

    @SlashCommand(name = "info", subcommand = "bot", description = "View information about the bot", guildOnly = true, requiredPermissions = {"BAN_MEMBERS"})
    public void botInfo(SlashCommandInteractionEvent event) {
        StringBuilder builder = new StringBuilder();
        SelfUser bot = NerdBotApp.getBot().getJDA().getSelfUser();
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();

        String botInfo = """
            - Bot name: %s (ID: %s)
            - Branch: `%s`
            - Container ID: `%s`
            - Environment: %s
            - Uptime: %s
            - Memory: %s / %s
            """.formatted(
                bot.getName(), bot.getId(),
                FileUtils.getBranchName(),
                FileUtils.getDockerContainerId(),
                Environment.getEnvironment(),
                TimeUtils.formatMsCompact(NerdBotApp.getBot().getUptime()),
                StringUtils.formatSize(usedMemory), StringUtils.formatSize(totalMemory)
            );

        event.reply(botInfo).setEphemeral(true).queue();
    }

    // TODO: /info greenlit command to view greenlit messages, pagination + selection menu handling

    @SlashCommand(name = "info", subcommand = "server", description = "View some information about the server", guildOnly = true, requiredPermissions = {"BAN_MEMBERS"})
    public void serverInfo(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        StringBuilder builder = new StringBuilder();

        AtomicInteger staff = new AtomicInteger();
        AtomicInteger grapes = new AtomicInteger();
        AtomicInteger nerds = new AtomicInteger();

        for (String roleName : Utils.SPECIAL_ROLES) {
            RoleManager.getRole(roleName).ifPresentOrElse(role -> staff.addAndGet(guild.getMembersWithRoles(role).size()),
                () -> log.warn("Role {} not found", roleName)
            );
        }

        RoleConfig roleConfig = NerdBotApp.getBot().getConfig().getRoleConfig();

        RoleManager.getRoleById(roleConfig.getModeratorRoleId()).ifPresentOrElse(role -> grapes.set(guild.getMembersWithRoles(role).size()),
            () -> log.warn("Role {} not found", "Grape"));

        RoleManager.getRoleById(roleConfig.getOrangeRoleId()).ifPresentOrElse(role -> nerds.set(guild.getMembersWithRoles(role).size()),
            () -> log.warn("Role {} not found", "Orange"));

        String serverInfo = """
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
                staff.get(),
                grapes.get(),
                nerds.get()
            );

        event.reply(serverInfo).setEphemeral(true).queue();
    }
}
