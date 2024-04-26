package net.hypixel.skyblocknerds.discordbot.configuration;

import lombok.Getter;
import lombok.Setter;
import net.hypixel.skyblocknerds.api.configuration.IConfiguration;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
public class GuildConfiguration implements IConfiguration {

    /**
     * The {@link net.dv8tion.jda.api.entities.Guild} ID of the primary guild
     */
    @NotNull
    private String primaryGuildId = "";
}
