package net.hypixel.nerdbot.command;

import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.bot.Environment;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.bot.config.EmojiConfig;
import net.hypixel.nerdbot.bot.config.RoleConfig;
import net.hypixel.nerdbot.cache.EmojiCache;
import net.hypixel.nerdbot.repository.GreenlitMessageRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.FileUtils;
import net.hypixel.nerdbot.util.StringUtils;
import net.hypixel.nerdbot.util.TimeUtils;
import net.hypixel.nerdbot.util.Utils;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;
import net.hypixel.nerdbot.util.pagination.PaginatedResponse;
import net.hypixel.nerdbot.util.pagination.PaginationManager;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
public class InfoCommands {


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

    @SlashCommand(name = "info", subcommand = "greenlit", description = "View greenlit suggestions", guildOnly = true, requiredPermissions = {"BAN_MEMBERS"})
    public void greenlitInfo(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        GreenlitMessageRepository greenlitRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(GreenlitMessageRepository.class);

        greenlitRepository.getAllDocumentsAsync().thenAccept(greenlitMessages -> {
            if (greenlitMessages.isEmpty()) {
                event.getHook().editOriginal("No greenlit suggestions found!").queue();
                return;
            }

            // Sort by suggestion timestamp
            List<GreenlitMessage> sortedMessages = greenlitMessages.stream()
                .sorted(Comparator.comparingLong(GreenlitMessage::getSuggestionTimestamp).reversed())
                .collect(Collectors.toList());

            PaginatedResponse<GreenlitMessage> pagination = PaginatedResponse.forEmbeds(
                sortedMessages,
                5,
                pageItems -> buildGreenlitEmbed(pageItems).build(),
                "info-page"
            );

            pagination.sendMessage(event);

            event.getHook().retrieveOriginal().queue(message ->
                PaginationManager.registerPagination(message.getId(), pagination)
            );
        }).exceptionally(throwable -> {
            log.error("Error loading greenlit messages", throwable);
            event.getHook().editOriginal("Failed to load greenlit suggestions: " + throwable.getMessage()).queue();
            return null;
        });
    }

    private EmbedBuilder buildGreenlitEmbed(List<GreenlitMessage> greenlitMessages) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Color.GREEN)
            .setTitle("Greenlit Suggestions")
            .setDescription("Recent greenlit suggestions");

        for (GreenlitMessage message : greenlitMessages) {
            String title = message.getSuggestionTitle();
            if (title.length() > 100) {
                title = title.substring(0, 97) + "...";
            }

            String content = message.getSuggestionContent();
            if (content.length() > 200) {
                content = content.substring(0, 197) + "...";
            }

            String tags = message.getTags().isEmpty() ? "No tags" : String.join(", ", message.getTags());
            String votes = String.format("%s %d | %s %d | %s %d",
                EmojiCache.getFormattedEmoji(EmojiConfig::getAgreeEmojiId), message.getAgrees(),
                EmojiCache.getFormattedEmoji(EmojiConfig::getDisagreeEmojiId), message.getDisagrees(),
                EmojiCache.getFormattedEmoji(EmojiConfig::getNeutralEmojiId), message.getNeutrals());

            String fieldValue = String.format(
                "%s\n\n**Tags:** %s\n**Votes:** %s\n**Date:** %s\n[View](%s)",
                content,
                tags,
                votes,
                DiscordTimestamp.toRelativeTimestamp(message.getSuggestionTimestamp()),
                message.getSuggestionUrl()
            );

            embedBuilder.addField(title, fieldValue, false);
        }

        return embedBuilder;
    }

    @SlashCommand(name = "info", subcommand = "server", description = "View some information about the server", guildOnly = true, requiredPermissions = {"BAN_MEMBERS"})
    public void serverInfo(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();

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
