package net.hypixel.nerdbot.app.command;

import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.hypixel.nerdbot.app.post.MarkdownPostService;
import net.hypixel.nerdbot.discord.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class PostCommands {

    private static final int MAX_ATTACHMENT_SIZE_BYTES = 512 * 1_024; // 512 KB

    private final MarkdownPostService markdownPostService = new MarkdownPostService();

    @SlashCommand(name = "post", subcommand = "markdown", description = "Post the contents of a markdown file into the current channel", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void postMarkdown(SlashCommandInteractionEvent event, @SlashOption(description = "The markdown file to post") Message.Attachment attachment) {
        String fileName = attachment.getFileName().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".md") && !fileName.endsWith(".markdown") && !fileName.endsWith(".txt")) {
            event.reply("Please upload a `.md`, `.markdown`, or `.txt` file.").setEphemeral(true).queue();
            return;
        }

        if (attachment.getSize() > MAX_ATTACHMENT_SIZE_BYTES) {
            event.reply("Attachment is too large! Maximum size is " + StringUtils.formatSize(MAX_ATTACHMENT_SIZE_BYTES) + ".").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).complete();

        String content;
        try (InputStream inputStream = attachment.getProxy().download().join()) {
            content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to read attachment '{}'", attachment.getFileName(), e);
            event.getHook().editOriginal("Failed to read the uploaded file: " + e.getMessage()).queue();
            return;
        }

        if (content.isBlank()) {
            event.getHook().editOriginal("The uploaded file is empty!").queue();
            return;
        }

        List<String> sections = markdownPostService.render(content);

        if (sections.isEmpty()) {
            event.getHook().editOriginal("No content found after processing the file.").queue();
            return;
        }

        MessageChannel channel = event.getChannel();

        CompletableFuture<?> chain = CompletableFuture.completedFuture(null);
        for (String section : sections) {
            chain = chain.thenCompose(ignored -> channel.sendMessage(section).submit());
        }

        chain.thenRun(() -> event.getHook().editOriginal("Posted " + sections.size() + " message(s) from `" + attachment.getFileName() + "`.").queue())
            .exceptionally(throwable -> {
                log.error("Failed to post markdown sections from '{}'", attachment.getFileName(), throwable);
                event.getHook().editOriginal("An error occurred while posting the markdown content.").queue();
                return null;
            });
    }
}
