package net.hypixel.nerdbot.app.command;

import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Slf4j
public class PostCommands {

    private static final int MAX_ATTACHMENT_SIZE_BYTES = 512 * 1_024; // 512 KB
    private static final int DISCORD_MESSAGE_LIMIT = 2_000;

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("</?[a-zA-Z][^>]*>");
    private static final Pattern HORIZONTAL_RULE_PATTERN = Pattern.compile("^[-*_]{3,}\\s*$", Pattern.MULTILINE);
    private static final Pattern DEEP_HEADING_PATTERN = Pattern.compile("^#{4,6}\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern LATEX_BLOCK_PATTERN = Pattern.compile("\\$\\$\\s*\n(.*?)\n\\s*\\$\\$", Pattern.DOTALL);
    private static final Pattern LATEX_INLINE_PATTERN = Pattern.compile("\\$([^$]+)\\$");
    private static final Pattern BLANK_LINES_PATTERN = Pattern.compile("(?m)^\\s*$(\n\\s*$)+");
    private static final Pattern H1_H2_PATTERN = Pattern.compile("(?=^#{1,2}(?!#) )", Pattern.MULTILINE);
    private static final Pattern H3_PATTERN = Pattern.compile("(?=^###(?!#) )", Pattern.MULTILINE);
    private static final Pattern H4_PATTERN = Pattern.compile("(?=^####(?!#) )", Pattern.MULTILINE);
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile("\n\n+");

    @SlashCommand(name = "post", subcommand = "markdown", description = "Post the contents of a markdown file into the current channel", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void postMarkdown(SlashCommandInteractionEvent event, @SlashOption(description = "The markdown file to post") Message.Attachment attachment) {
        String fileName = attachment.getFileName().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".md") && !fileName.endsWith(".markdown") && !fileName.endsWith(".txt")) {
            event.reply("Please upload a `.md`, `.markdown`, or `.txt` file.").setEphemeral(true).queue();
            return;
        }

        if (attachment.getSize() > MAX_ATTACHMENT_SIZE_BYTES) {
            event.reply("Attachment is too large! Maximum size is 512 KB.").setEphemeral(true).queue();
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

        content = convertToDiscordMarkdown(content);

        List<String> sections = splitIntoSections(content);

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

    private String convertToDiscordMarkdown(String markdown) {
        String result = HTML_TAG_PATTERN.matcher(markdown).replaceAll("");
        result = HORIZONTAL_RULE_PATTERN.matcher(result).replaceAll("");
        result = DEEP_HEADING_PATTERN.matcher(result).replaceAll("**$1**");
        result = LATEX_BLOCK_PATTERN.matcher(result).replaceAll("$1");
        result = LATEX_INLINE_PATTERN.matcher(result).replaceAll("$1");
        result = BLANK_LINES_PATTERN.matcher(result).replaceAll("\n");
        return result;
    }

    private List<String> splitIntoSections(String content) {
        List<String> rawSections = splitByPattern(content, H1_H2_PATTERN);

        List<String> result = new ArrayList<>();
        for (String section : rawSections) {
            if (section.length() <= DISCORD_MESSAGE_LIMIT) {
                result.add(section);
            } else {
                for (String subSection : splitByPattern(section, H3_PATTERN)) {
                    if (subSection.length() <= DISCORD_MESSAGE_LIMIT) {
                        result.add(subSection);
                    } else {
                        for (String subSubSection : splitByPattern(subSection, H4_PATTERN)) {
                            if (subSubSection.length() <= DISCORD_MESSAGE_LIMIT) {
                                result.add(subSubSection);
                            } else {
                                result.addAll(splitByParagraphs(subSubSection));
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    private List<String> splitByPattern(String content, Pattern pattern) {
        String[] parts = pattern.split(content);
        List<String> sections = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                sections.add(trimmed);
            }
        }
        return sections;
    }

    private List<String> splitByParagraphs(String content) {
        String[] paragraphs = PARAGRAPH_PATTERN.split(content);
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.strip();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (current.isEmpty()) {
                current.append(trimmed);
            } else if (current.length() + 2 + trimmed.length() <= DISCORD_MESSAGE_LIMIT) {
                current.append("\n\n").append(trimmed);
            } else {
                result.add(current.toString());
                current = new StringBuilder(trimmed);
            }
        }

        if (!current.isEmpty()) {
            result.add(current.toString());
        }

        List<String> safe = new ArrayList<>();
        for (String chunk : result) {
            if (chunk.length() <= DISCORD_MESSAGE_LIMIT) {
                safe.add(chunk);
            } else {
                safe.addAll(splitByLines(chunk));
            }
        }

        return safe;
    }

    private List<String> splitByLines(String content) {
        String[] lines = content.split("\n");
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            // Single line exceeds limit, hard truncate to avoid runtime failure
            if (line.length() > DISCORD_MESSAGE_LIMIT) {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current = new StringBuilder();
                }
                for (int i = 0; i < line.length(); i += DISCORD_MESSAGE_LIMIT) {
                    result.add(line.substring(i, Math.min(i + DISCORD_MESSAGE_LIMIT, line.length())));
                }
                continue;
            }

            if (current.isEmpty()) {
                current.append(line);
            } else if (current.length() + 1 + line.length() <= DISCORD_MESSAGE_LIMIT) {
                current.append("\n").append(line);
            } else {
                result.add(current.toString());
                current = new StringBuilder(line);
            }
        }

        if (!current.isEmpty()) {
            result.add(current.toString());
        }

        return result;
    }
}
