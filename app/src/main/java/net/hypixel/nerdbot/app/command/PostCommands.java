package net.hypixel.nerdbot.app.command;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.hypixel.nerdbot.app.command.util.CommandReplies;
import net.hypixel.nerdbot.app.post.MarkdownPostService;
import net.hypixel.nerdbot.discord.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class PostCommands {

    static final int MAX_ATTACHMENT_SIZE_BYTES = 512 * 1_024;

    private final MarkdownPostService markdownPostService;

    @Inject
    public PostCommands(MarkdownPostService markdownPostService) {
        this.markdownPostService = markdownPostService;
    }

    @SlashCommand(name = "post", subcommand = "markdown", description = "Post the contents of a markdown file into the current channel", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void postMarkdown(SlashCommandInteractionEvent event, @SlashOption(description = "The markdown file to post") Message.Attachment attachment) {
        Optional<String> rejection = rejection(attachment.getFileName(), attachment.getSize());
        if (rejection.isPresent()) {
            CommandReplies.replyPrivately(event, rejection.get());
            return;
        }

        InteractionHook hook = CommandReplies.deferPrivately(event);
        post(hook, event.getChannel(), attachment.getFileName(), readText(attachment));
    }

    /** Why the file can't be posted, if it can't. */
    static Optional<String> rejection(String fileName, long sizeBytes) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (!name.endsWith(".md") && !name.endsWith(".markdown") && !name.endsWith(".txt")) {
            return Optional.of("Please upload a `.md`, `.markdown`, or `.txt` file.");
        }

        if (sizeBytes > MAX_ATTACHMENT_SIZE_BYTES) {
            return Optional.of("Attachment is too large! Maximum size is " + StringUtils.formatSize(MAX_ATTACHMENT_SIZE_BYTES) + ".");
        }

        return Optional.empty();
    }

    /** Renders the downloaded text and posts it section by section, in order. */
    void post(InteractionHook hook, MessageChannel channel, String fileName, CompletableFuture<String> download) {
        CompletableFuture<String> outcome = download.thenCompose(content -> {
            if (content.isBlank()) {
                return CompletableFuture.completedFuture("The uploaded file is empty!");
            }

            List<String> sections = markdownPostService.render(content);
            if (sections.isEmpty()) {
                return CompletableFuture.completedFuture("No content found after processing the file.");
            }

            CompletableFuture<?> chain = CompletableFuture.completedFuture(null);
            for (String section : sections) {
                chain = chain.thenCompose(ignored -> channel.sendMessage(section).submit());
            }
            return chain.thenApply(ignored -> "Posted " + sections.size() + " message(s) from `" + fileName + "`.");
        });

        CommandReplies.finish(outcome, hook, "post markdown from " + fileName,
            "An error occurred while reading or posting the file. Check the channel before trying again, as some sections may have been posted.",
            message -> hook.editOriginal(message).queue());
    }

    private static CompletableFuture<String> readText(Message.Attachment attachment) {
        return attachment.getProxy().download().thenApply(stream -> {
            try (InputStream input = stream) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }
}
