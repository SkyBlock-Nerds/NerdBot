package net.hypixel.skyblocknerds.discordbot.configuration;

import lombok.Getter;
import lombok.Setter;
import net.hypixel.skyblocknerds.api.configuration.IConfiguration;
import net.hypixel.skyblocknerds.discordbot.embed.auditlog.AuditLog;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class GuildConfiguration implements IConfiguration {

    /**
     * The {@link net.dv8tion.jda.api.entities.Guild} ID of the primary guild
     */
    @NotNull
    private String primaryGuildId = "";

    /**
     * A {@link List} of all {@link net.dv8tion.jda.api.entities.channel.concrete.ForumChannel} IDs
     * that the bot should use to curate suggestions
     */
    private List<String> suggestionForumIds = new ArrayList<>();

    /**
     * The {@link net.dv8tion.jda.api.entities.channel.concrete.TextChannel} ID of the channel that the bot should use to log {@link AuditLog} events
     */
    private String auditLogChannelId = "";
}
