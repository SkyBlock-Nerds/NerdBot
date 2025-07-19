package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.language.TranslationManager;
import net.hypixel.nerdbot.bot.config.objects.CustomForumTag;
import net.hypixel.nerdbot.bot.config.suggestion.SuggestionConfig;
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

@Log4j2
public class ChannelCommands extends ApplicationCommand {

    @JDASlashCommand(name = "archive", subcommand = "channel", description = "Archives a channel and exports its contents to a file", defaultLocked = true)
    public void archive(GuildSlashEvent event, @AppOption TextChannel channel) {
        event.deferReply(true).complete();
        TranslationManager.edit(event.getHook(), "commands.archive.start", channel.getAsMention());

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
                    event.getHook().editOriginal(TranslationManager.translate("commands.archive.complete", channel.getAsMention()))
                        .setFiles(FileUpload.fromData(file))
                        .queue();
                }
            } catch (IOException exception) {
                TranslationManager.reply(event, "commands.archive.error", channel.getAsMention());
                log.error("An error occurred when archiving the channel " + channel.getId() + "!", exception);
            }
        });
    }

    @JDASlashCommand(name = "lock", description = "Locks the thread that the command is executed in", defaultLocked = true)
    public void lockThread(GuildSlashEvent event) {
        event.deferReply(true).complete();
        InteractionHook hook = event.getHook();

        TranslationManager.edit(hook, "commands.lock.start");

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.edit(hook, "generic.user_not_found");
            return;
        }

        if (!(event.getChannel() instanceof ThreadChannel threadChannel) || !(threadChannel.getParentChannel() instanceof ForumChannel)) {
            TranslationManager.edit(hook, discordUser, "commands.only_available_in_threads");
            return;
        }

        ForumChannel forumChannel = threadChannel.getParentChannel().asForumChannel();
        SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();
        boolean isSuggestion = threadChannel.getParentChannel().getId().equalsIgnoreCase(suggestionConfig.getForumChannelId());

        if (isSuggestion) {
            if (!DiscordUtils.hasTagByName(forumChannel, suggestionConfig.getReviewedTag())) {
                TranslationManager.edit(hook, discordUser, "commands.lock.no_tag", suggestionConfig.getReviewedTag());
                return;
            }

            handleTagAndLock(event, discordUser, threadChannel, forumChannel, suggestionConfig.getReviewedTag());
        } else {
            Optional<CustomForumTag> customForumTag = NerdBotApp.getBot().getConfig().getChannelConfig().getCustomForumTags().stream()
                .filter(tag -> tag.getOwnerId() != null && tag.getOwnerId().equals(discordUser.getDiscordId()))
                .findFirst();

            if (customForumTag.isPresent()) {
                handleTagAndLock(event, discordUser, threadChannel, forumChannel, customForumTag.get().getName());
            } else {
                handleTagAndLock(event, discordUser, threadChannel, forumChannel, suggestionConfig.getReviewedTag());
            }
        }
    }

    private void handleTagAndLock(GuildSlashEvent event, DiscordUser discordUser, ThreadChannel threadChannel, ForumChannel forumChannel, String tagName) {
        InteractionHook hook = event.getHook();

        if (!DiscordUtils.hasTagByName(forumChannel, tagName)) {
            TranslationManager.edit(hook, discordUser, "commands.lock.no_tag", tagName);
            return;
        }

        ThreadChannelManager threadManager = threadChannel.getManager();
        ForumTag tag = DiscordUtils.getTagByName(forumChannel, tagName);

        if (!threadChannel.getAppliedTags().contains(tag)) {
            List<ForumTag> appliedTags = new ArrayList<>(threadChannel.getAppliedTags());
            appliedTags.add(tag);
            threadManager.setAppliedTags(appliedTags).complete();
            threadManager.setLocked(true).complete();
            event.getChannel().sendMessage(String.format("%s applied the %s tag and locked this suggestion!", event.getUser().getAsMention(), tag.getName())).queue();
            TranslationManager.edit(hook, discordUser, "commands.lock.success");
        } else {
            TranslationManager.edit(hook, discordUser, "commands.lock.already_tagged");
        }
    }
}
