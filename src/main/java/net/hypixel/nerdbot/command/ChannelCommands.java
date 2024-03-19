package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.curator.Curator;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.language.TranslationManager;
import net.hypixel.nerdbot.bot.config.EmojiConfig;
import net.hypixel.nerdbot.bot.config.channel.AlphaProjectConfig;
import net.hypixel.nerdbot.bot.config.suggestion.SuggestionConfig;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.csv.CSVData;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class ChannelCommands extends ApplicationCommand {

    @JDASlashCommand(name = "archive", subcommand = "channel", description = "Archives a channel and exports its contents to a file", defaultLocked = true)
    public void archive(GuildSlashEvent event, @AppOption TextChannel channel) {
        event.deferReply(true).complete();
        event.getHook().editOriginal("Archiving channel " + channel.getAsMention() + "...\nIf no file appears, ask a developer!").queue();

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!event.getHook().isExpired()) {
                    event.getHook().editOriginal("Archive interaction timeout").queue();
                }
            }
        }, (14 * 60 * 1_000) + (59 * 1_000));

        CSVData csvData = new CSVData(List.of("Timestamp", "Username", "User ID", "Message ID", "Thread ID", "Thread Name", "Message Content"));

        channel.getIterableHistory().forEachAsync(message -> {
            String formattedTimestamp = message.getTimeCreated().format(Util.REGULAR_DATE_FORMAT);

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

            log.debug("Archived message " + message.getId() + " from channel " + channel.getId() + "!");
            return true;
        }).thenAccept(unused -> {
            try {
                File file = Util.createTempFile(String.format("archive-%s-%s-%s.csv", channel.getName(), channel.getId(), Util.FILE_NAME_DATE_FORMAT.format(Instant.now())), csvData.toCSV());
                log.info("Finished archiving channel " + channel.getName() + "! File located at: " + file.getAbsolutePath());

                if (!event.getHook().isExpired()) {
                    event.getHook().editOriginal("Finished archiving channel " + channel.getAsMention() + "!\nUploading file...")
                        .setFiles(FileUpload.fromData(file))
                        .queue();
                }
            } catch (IOException exception) {
                TranslationManager.reply(event, "commands.archive.error");
                log.error("An error occurred when archiving the channel " + channel.getId() + "!", exception);
            }
            timer.cancel();
        });
    }

    @JDASlashCommand(name = "flared", description = "Add the Flared tag to a suggestion and lock it", defaultLocked = true)
    public void flareSuggestion(GuildSlashEvent event) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getUser().getId());

        if (discordUser == null) {
            TranslationManager.reply(event, "generic.user_not_found");
            return;
        }

        Channel channel = event.getChannel();
        if (!(channel instanceof ThreadChannel threadChannel) || !(threadChannel.getParentChannel() instanceof ForumChannel forumChannel)) {
            TranslationManager.reply(event, discordUser, "commands.only_available_in_threads");
            return;
        }

        AlphaProjectConfig alphaProjectConfig = NerdBotApp.getBot().getConfig().getAlphaProjectConfig();
        if (!Util.hasTagByName(forumChannel, alphaProjectConfig.getFlaredTag())) {
            TranslationManager.reply(event, discordUser, "commands.flared.no_tag", "Flared");
            return;
        }

        ForumTag flaredTag = Util.getTagByName(forumChannel, alphaProjectConfig.getFlaredTag());
        if (threadChannel.getAppliedTags().contains(flaredTag)) {
            TranslationManager.reply(event, discordUser, "commands.flared.already_tagged");
            return;
        }

        List<ForumTag> appliedTags = new ArrayList<>(threadChannel.getAppliedTags());
        appliedTags.add(flaredTag);

        threadChannel.getManager()
            .setLocked(true)
            .setAppliedTags(appliedTags)
            .queue();

        TranslationManager.reply(event, discordUser, "commands.flared.tagged", event.getUser().getAsMention(), flaredTag.getName());
    }

    @JDASlashCommand(name = "lock", description = "Locks the thread that the command is executed in", defaultLocked = true)
    public void lockThread(GuildSlashEvent event) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.reply(event, "generic.user_not_found");
            return;
        }

        if (!(event.getChannel() instanceof ThreadChannel threadChannel)) {
            TranslationManager.reply(event, discordUser, "commands.only_available_in_threads");
            return;
        }

        SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();
        boolean locked = threadChannel.isLocked();
        ThreadChannelManager threadManager = threadChannel.getManager();

        if (threadChannel.getParentChannel() instanceof ForumChannel forumChannel) { // Is thread inside a forum?
            // Add Reviewed Tag
            if (Util.hasTagByName(forumChannel, suggestionConfig.getReviewedTag())) { // Does forum contain the reviewed tag?
                if (!Util.hasTagByName(threadChannel, suggestionConfig.getReviewedTag())) { // Does thread not currently have reviewed tag?
                    List<ForumTag> forumTags = new ArrayList<>(threadChannel.getAppliedTags());
                    forumTags.add(Util.getTagByName(forumChannel, suggestionConfig.getReviewedTag()));
                    threadManager = threadManager.setAppliedTags(forumTags);
                }
            }

            // Add Greenlit Tag
            if (Util.hasTagByName(forumChannel, suggestionConfig.getGreenlitTag())) { // Does forum contain the greenlit tag?
                if (!Util.hasTagByName(threadChannel, suggestionConfig.getGreenlitTag())) { // Does thread not currently have greenlit tag?
                    List<ForumTag> forumTags = new ArrayList<>();

                    Util.getFirstMessage(threadChannel).ifPresent(firstMessage -> {
                        EmojiConfig emojiConfig = NerdBotApp.getBot().getConfig().getEmojiConfig();

                        List<MessageReaction> reactions = firstMessage.getReactions()
                            .stream()
                            .filter(reaction -> reaction.getEmoji().getType() == Emoji.Type.CUSTOM)
                            .toList();

                        Map<String, Integer> votes = Stream.of(
                                emojiConfig.getAgreeEmojiId(),
                                emojiConfig.getNeutralEmojiId(),
                                emojiConfig.getDisagreeEmojiId()
                            )
                            .map(emojiId -> Pair.of(
                                emojiId,
                                reactions.stream()
                                    .filter(reaction -> reaction.getEmoji()
                                        .asCustom()
                                        .getId()
                                        .equalsIgnoreCase(emojiId)
                                    )
                                    .mapToInt(MessageReaction::getCount)
                                    .findFirst()
                                    .orElse(0)
                            ))
                            .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

                        int agree = votes.get(emojiConfig.getAgreeEmojiId());
                        int disagree = votes.get(emojiConfig.getDisagreeEmojiId());
                        double ratio = Curator.getRatio(agree, disagree);

                        if ((agree < suggestionConfig.getGreenlitThreshold()) || (ratio < suggestionConfig.getGreenlitRatio())) {
                            return;
                        }

                        forumTags.addAll(threadChannel.getAppliedTags());
                        forumTags.add(Util.getTagByName(forumChannel, suggestionConfig.getGreenlitTag()));
                    });

                    if (!forumTags.isEmpty()) {
                        threadManager = threadManager.setAppliedTags(forumTags);
                    }
                }
            }
        }

        threadManager.setLocked(!locked).queue(unused ->
                event.reply("This thread is now " + (!locked ? "locked" : "unlocked") + "!").queue(),
            throwable -> {
                TranslationManager.reply(event, discordUser, "commands.lock.error");
                log.error("An error occurred when locking the thread " + threadChannel.getId() + "!", throwable);
            });
    }
}
