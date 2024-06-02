package net.hypixel.skyblocknerds.discordbot.embed.auditlog;

import net.dv8tion.jda.api.EmbedBuilder;

public class AuditLog {

    private static EmbedBuilder createEmbed() {
        return new EmbedBuilder()
            .setTimestamp(java.time.Instant.now());
    }
}
