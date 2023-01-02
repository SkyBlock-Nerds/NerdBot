package net.hypixel.nerdbot.bot.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.dv8tion.jda.api.entities.Role;

import java.nio.channels.Channel;

@Getter
@Setter
@ToString
public class ModMailConfig {

    /**
     * The ID of the {@link Channel} where new Mod Mail requests will be sent
     */
    private String receivingChannelId;

    /**
     * The ID of the Mod Mail {@link Role}, used to ping for new requests and updates
     * to existing requests
     */
    private String roleId;
}
