package net.hypixel.nerdbot.app.command;

import com.github.tomtung.latex2unicode.LaTeX2Unicode;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.hypixel.nerdbot.discord.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
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
    private static final Pattern LATEX_TEXT_PATTERN = Pattern.compile("\\\\text\\{([^{}]*)}");
    private static final Pattern LATEX_TEXTBF_PATTERN = Pattern.compile("\\\\textbf\\{([^{}]*)}");
    private static final Pattern LATEX_TEXTIT_PATTERN = Pattern.compile("\\\\textit\\{([^{}]*)}");
    private static final Pattern LATEX_MATHRM_PATTERN = Pattern.compile("\\\\mathrm\\{([^{}]*)}");
    private static final Pattern LATEX_MATHBF_PATTERN = Pattern.compile("\\\\mathbf\\{([^{}]*)}");
    private static final Pattern LATEX_FRAC_PATTERN = Pattern.compile("\\\\frac\\{([^{}]*(?:\\{[^{}]*}[^{}]*)*)\\}\\{([^{}]*(?:\\{[^{}]*}[^{}]*)*)\\}");
    private static final Pattern LATEX_REMAINING_BRACES = Pattern.compile("\\{([^{}]*)}");
    private static final Pattern LATEX_UNKNOWN_COMMANDS = Pattern.compile("\\\\[a-zA-Z]+");
    private static final Pattern BLANK_LINES_PATTERN = Pattern.compile("(?m)^\\s*$(\n\\s*$)+");
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile("\n\n+");
    private static final List<Pattern> HEADING_SPLIT_PATTERNS = List.of(
        Pattern.compile("(?=^#{1,2}(?!#) )", Pattern.MULTILINE),
        Pattern.compile("(?=^###(?!#) )", Pattern.MULTILINE),
        Pattern.compile("(?=^####(?!#) )", Pattern.MULTILINE)
    );
    private static final Pattern TABLE_BLOCK_PATTERN = Pattern.compile("(?m)(^[ \\t]*\\|.+\\|[ \\t]*\n)(^[ \\t]*\\|[-|: ]+\\|[ \\t]*\n)((?:^[ \\t]*\\|.+\\|[ \\t]*\n?)+)");

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

        content = content.replace("\r\n", "\n").replace("\r", "\n");
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
        result = convertLatex(result, LATEX_BLOCK_PATTERN);
        result = convertLatex(result, LATEX_INLINE_PATTERN);
        result = convertTables(result);
        result = BLANK_LINES_PATTERN.matcher(result).replaceAll("\n");
        return result;
    }

    private String convertLatex(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String latex = matcher.group(1).strip();
            String converted = convertLatexExpression(latex);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(converted));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String convertLatexExpression(String latex) {
        String result = latex;

        // Handle structural commands the library doesn't support
        result = LATEX_TEXT_PATTERN.matcher(result).replaceAll("$1");
        result = LATEX_TEXTBF_PATTERN.matcher(result).replaceAll("$1");
        result = LATEX_TEXTIT_PATTERN.matcher(result).replaceAll("$1");
        result = LATEX_MATHRM_PATTERN.matcher(result).replaceAll("$1");
        result = LATEX_MATHBF_PATTERN.matcher(result).replaceAll("$1");

        // Handle \frac{a}{b} -> (a)/(b) since the library uses Unicode fractions that only work for single digits
        for (int i = 0; i < 10; i++) {
            String replaced = LATEX_FRAC_PATTERN.matcher(result).replaceAll("($1)/($2)");
            if (replaced.equals(result)) break;
            result = replaced;
        }

        // Let the library handle symbol conversion (Greek letters, operators, etc.)
        try {
            result = LaTeX2Unicode.convert(result);
        } catch (Exception e) {
            log.warn("Failed to convert LaTeX '{}', keeping original", latex, e);
        }

        // Clean up remaining braces and unknown commands the library didn't handle
        result = LATEX_REMAINING_BRACES.matcher(result).replaceAll("$1");
        result = LATEX_UNKNOWN_COMMANDS.matcher(result).replaceAll("");
        result = result.replaceAll(" {2,}", " ");

        return result.strip();
    }

    private String joinWrappedTableLines(String content) {
        String[] lines = content.split("\n");
        StringBuilder result = new StringBuilder();
        StringBuilder pendingLine = null;

        for (String line : lines) {
            String trimmed = line.strip();
            boolean startsWithPipe = trimmed.startsWith("|");
            boolean endsWithPipe = trimmed.endsWith("|");

            if (startsWithPipe && endsWithPipe) {
                if (pendingLine != null) {
                    result.append(pendingLine).append('\n');
                    pendingLine = null;
                }
                result.append(line).append('\n');
            } else if (startsWithPipe && !endsWithPipe) {
                if (pendingLine != null) {
                    result.append(pendingLine).append('\n');
                }
                pendingLine = new StringBuilder(trimmed);
            } else if (!startsWithPipe && pendingLine != null) {
                pendingLine.append(' ').append(trimmed);
                if (endsWithPipe) {
                    result.append(pendingLine).append('\n');
                    pendingLine = null;
                }
            } else {
                if (pendingLine != null) {
                    result.append(pendingLine).append('\n');
                    pendingLine = null;
                }
                result.append(line).append('\n');
            }
        }

        if (pendingLine != null) {
            result.append(pendingLine).append('\n');
        }

        return result.toString();
    }

    private String convertTables(String content) {
        content = joinWrappedTableLines(content);
        Matcher matcher = TABLE_BLOCK_PATTERN.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String headerLine = matcher.group(1).strip();
            String dataBlock = matcher.group(3).strip();

            List<String[]> rows = new ArrayList<>();
            rows.add(parseTableRow(headerLine));
            for (String line : dataBlock.split("\n")) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty()) {
                    rows.add(parseTableRow(trimmed));
                }
            }

            int colCount = rows.stream().mapToInt(r -> r.length).max().orElse(0);
            if (colCount == 0) {
                continue;
            }

            int[] widths = new int[colCount];
            for (String[] row : rows) {
                for (int i = 0; i < row.length && i < colCount; i++) {
                    widths[i] = Math.max(widths[i], row[i].length());
                }
            }

            StringBuilder table = new StringBuilder("\n```\n");

            table.append(buildBorder(widths, '-'));
            table.append(buildRow(rows.getFirst(), widths));
            table.append(buildBorder(widths, '='));

            for (int i = 1; i < rows.size(); i++) {
                table.append(buildRow(rows.get(i), widths));
                if (i < rows.size() - 1) {
                    table.append(buildBorder(widths, '-'));
                }
            }

            table.append(buildBorder(widths, '-'));
            table.append("```\n");

            matcher.appendReplacement(sb, Matcher.quoteReplacement(table.toString()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String[] parseTableRow(String line) {
        String stripped = line.strip();
        if (stripped.startsWith("|")) {
            stripped = stripped.substring(1);
        }
        if (stripped.endsWith("|")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        return Arrays.stream(stripped.split("\\|"))
            .map(String::strip)
            .toArray(String[]::new);
    }

    private String buildBorder(int[] widths, char fill) {
        StringBuilder sb = new StringBuilder();
        sb.append('+');
        for (int width : widths) {
            sb.append(String.valueOf(fill).repeat(width + 2));
            sb.append('+');
        }
        sb.append('\n');
        return sb.toString();
    }

    private String buildRow(String[] cells, int[] widths) {
        StringBuilder sb = new StringBuilder();
        sb.append('|');
        for (int i = 0; i < widths.length; i++) {
            String cell = i < cells.length ? cells[i] : "";
            sb.append(' ');
            sb.append(cell);
            sb.append(" ".repeat(widths[i] - cell.length()));
            sb.append(" |");
        }
        sb.append('\n');
        return sb.toString();
    }

    private List<String> splitIntoSections(String content) {
        return splitRecursive(content, 0);
    }

    private List<String> splitRecursive(String content, int depth) {
        if (content.length() <= DISCORD_MESSAGE_LIMIT) {
            return List.of(content);
        }
        if (depth >= HEADING_SPLIT_PATTERNS.size()) {
            return splitByParagraphs(content);
        }

        List<String> result = new ArrayList<>();
        for (String section : splitByPattern(content, HEADING_SPLIT_PATTERNS.get(depth))) {
            result.addAll(splitRecursive(section, depth + 1));
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
