package net.hypixel.nerdbot.app.command.util;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;

import java.time.format.DateTimeFormatter;
import java.util.List;

public record MessageExport(String line, String contentWithAttachments, String attachmentSuffix) {

    public static MessageExport from(Message message, boolean includeMetadata) {
        String authorName = message.getAuthor().getName();
        String timestamp = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(message.getTimeCreated());
        List<String> attachmentUrls = message.getAttachments().stream().map(Attachment::getUrl).toList();
        String attachmentSuffix = attachmentUrls.isEmpty() ? "" : " [Attachments: " + String.join(", ", attachmentUrls) + "]";
        String contentWithAttachments = message.getContentRaw() + attachmentSuffix;
        String line = includeMetadata
            ? String.format("[%s] %s: %s%s", timestamp, authorName, message.getContentRaw(), attachmentSuffix)
            : contentWithAttachments;

        return new MessageExport(line, contentWithAttachments, attachmentSuffix);
    }
}
