package net.hypixel.nerdbot.discord.command;

import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.BotEnvironment;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.config.objects.CustomForumTag;
import net.hypixel.nerdbot.config.objects.ForumAutoTag;
import net.hypixel.nerdbot.config.suggestion.SuggestionConfig;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.DiscordUtils;
import net.hypixel.nerdbot.util.FileUtils;
import net.hypixel.nerdbot.util.StringUtils;
import net.hypixel.nerdbot.util.csv.CSVData;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

@Slf4j
public class ChannelCommands {

    @SlashCommand(name = "archive", subcommand = "channel", description = "Archives a channel and exports its contents to a file", guildOnly = true, requiredPermissions = {"MANAGE_CHANNEL"})
    public void archive(SlashCommandInteractionEvent event, @SlashOption TextChannel channel) {
        event.deferReply(true).complete();
        event.getHook().editOriginal(String.format("Archiving channel %s! If no file appears here, please contact a bot developer.", channel.getAsMention())).queue();

        CSVData csvData = new CSVData(List.of("Timestamp", "Username", "User ID", "Message ID", "Thread ID", "Thread Name", "Message Content"));

        AtomicInteger total = new AtomicInteger(0);
        channel.getIterableHistory().forEachAsync(message -> {
            String formattedTimestamp = message.getTimeCreated().format(FileUtils.REGULAR_DATE_FORMAT);
            String messageContent = message.getContentRaw().replace("\"", "\"\"");

            if (!message.getAttachments().isEmpty()) {
                if (!messageContent.isEmpty()) {
                    messageContent += "\n";
                }
                messageContent += "Attachments:\n" + message.getAttachments().stream().map(Message.Attachment::getUrl).collect(Collectors.joining("\n"));
            }

            if (message.getContentRaw().isEmpty() && !message.getEmbeds().isEmpty()) {
                if (!messageContent.isEmpty()) {
                    messageContent += "\n";
                }
                messageContent += "Contains " + message.getEmbeds().size() + " embed" + (message.getEmbeds().size() == 1 ? "" : "s");
            }

            if (message.getStartedThread() != null) {
                csvData.addRow(List.of(
                    formattedTimestamp,
                    message.getAuthor().getName(),
                    message.getAuthor().getId(),
                    message.getId(),
                    message.getStartedThread().getId(),
                    message.getStartedThread().getName(),
                    "\"" + messageContent + "\""
                ));
            } else {
                csvData.addRow(List.of(
                    formattedTimestamp,
                    message.getAuthor().getName(),
                    message.getAuthor().getId(),
                    message.getId(),
                    "\\N",
                    "\\N",
                    "\"" + messageContent + "\""
                ));
            }

            if (total.incrementAndGet() % (total.get() < 1000 ? 100 : (int) Math.pow(10, String.valueOf(total.get()).length() - 1)) == 0) {
                log.info("Archiving channel " + channel.getName() + " (ID: " + channel.getId() + ") - processed " + StringUtils.COMMA_SEPARATED_FORMAT.format(total.get()) + " message" + (total.get() == 1 ? "" : "s") + " so far!");
            }

            return true;
        }).thenAccept(unused -> {
            try {
                File file = FileUtils.createTempFile(String.format("archive-%s-%s-%s.csv", channel.getName(), channel.getId(), FileUtils.FILE_NAME_DATE_FORMAT.format(Instant.now())), csvData.toCSV());
                log.info("Finished archiving channel " + channel.getName() + " (ID: " + channel.getId() + ")! File located at: " + file.getAbsolutePath());

                if (!event.getHook().isExpired()) {
                    event.getHook().editOriginal(String.format("Finished archiving channel %s! The file should appear below.", channel.getAsMention()))
                        .setFiles(FileUpload.fromData(file))
                        .queue();
                }
            } catch (IOException exception) {
                event.reply(String.format("An error occurred while archiving channel %s! Please check the logs for more information.", channel.getAsMention())).queue();
                log.error("An error occurred when archiving the channel " + channel.getId() + "!", exception);
            }
        });
    }

    @SlashCommand(name = "lock", description = "Locks the thread that the command is executed in", guildOnly = true, requiredPermissions = {"MANAGE_THREADS"})
    public void lockThread(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();
        InteractionHook hook = event.getHook();

        hook.editOriginal("Locking...").queue();

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        discordUserRepository.findByIdAsync(event.getMember().getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    hook.editOriginal("User not found").queue();
                    return;
                }

                if (!(event.getChannel() instanceof ThreadChannel threadChannel) || !(threadChannel.getParentChannel() instanceof ForumChannel)) {
                    hook.editOriginal("This command is only available in threads!").queue();
                    return;
                }

                ForumChannel forumChannel = threadChannel.getParentChannel().asForumChannel();
                SuggestionConfig suggestionConfig = DiscordBotEnvironment.getBot().getConfig().getSuggestionConfig();
                boolean isSuggestion = threadChannel.getParentChannel().getId().equalsIgnoreCase(suggestionConfig.getForumChannelId());

                if (isSuggestion) {
                    if (!DiscordUtils.hasTagByName(forumChannel, suggestionConfig.getReviewedTag())) {
                        hook.editOriginal(String.format("I could not find a tag with the name `%s`!", suggestionConfig.getReviewedTag())).queue();
                        return;
                    }

                    handleTagAndLock(event, discordUser, threadChannel, forumChannel, suggestionConfig.getReviewedTag());
                } else {
                    Optional<CustomForumTag> customForumTag = DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getCustomForumTags().stream()
                        .filter(tag -> tag.getOwnerId() != null && tag.getOwnerId().equals(discordUser.getDiscordId()))
                        .findFirst();

                    if (customForumTag.isPresent()) {
                        handleTagAndLock(event, discordUser, threadChannel, forumChannel, customForumTag.get().getName());
                    } else {
                        handleTagAndLock(event, discordUser, threadChannel, forumChannel, suggestionConfig.getReviewedTag());
                    }
                }
            })
            .exceptionally(throwable -> {
                log.error("Error loading user for channel lock", throwable);
                hook.editOriginal("Failed to load user data").queue();
                return null;
            });
    }

    private void handleTagAndLock(SlashCommandInteractionEvent event, DiscordUser discordUser, ThreadChannel threadChannel, ForumChannel forumChannel, String tagName) {
        InteractionHook hook = event.getHook();

        if (!DiscordUtils.hasTagByName(forumChannel, tagName)) {
            hook.editOriginal(String.format("I could not find a tag with the name `%s`!", tagName)).queue();
            return;
        }

        ThreadChannelManager threadManager = threadChannel.getManager();
        ForumTag tag = DiscordUtils.getTagByName(forumChannel, tagName);

        if (!threadChannel.getAppliedTags().contains(tag)) {
            List<ForumTag> appliedTags = new ArrayList<>(threadChannel.getAppliedTags());
            appliedTags.add(tag);

            // Check for auto-tag swap configuration
            ForumAutoTag autoTagConfig = DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getForumAutoTagConfig(forumChannel.getId());
            if (autoTagConfig != null && autoTagConfig.getReviewTagName().equalsIgnoreCase(tagName)) {
                ForumTag defaultTag = DiscordUtils.getTagByName(forumChannel, autoTagConfig.getDefaultTagName());
                if (defaultTag != null && appliedTags.contains(defaultTag)) {
                    appliedTags.remove(defaultTag);
                    log.info("Removed auto-tag '{}' from thread '{}' (ID: {}) when '{}' tag was applied via /lock", autoTagConfig.getDefaultTagName(), threadChannel.getName(), threadChannel.getId(), tagName);
                }
            }

            threadManager.setAppliedTags(appliedTags).complete();
            threadManager.setLocked(true).complete();
            event.getChannel().sendMessage(String.format("%s applied the %s tag and locked this suggestion!", event.getUser().getAsMention(), tag.getName())).queue();
            hook.editOriginal("Successfully locked thread!").queue();
        } else {
            hook.editOriginal("This thread is already tagged!").queue();
        }
    }
}
