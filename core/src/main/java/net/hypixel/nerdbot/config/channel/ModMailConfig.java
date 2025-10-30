package net.hypixel.nerdbot.config.channel;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ModMailConfig {

    /**
     * The ID of the channel where new Mod Mail requests will be sent
     */
    private String channelId = "";

    /**
     * The ID of the Mod Mail role, used to ping for new requests and updates
     * to existing requests
     */
    private String roleId = "";

    /**
     * The Webhook ID for the Mod Mail channel, used to represent users
     */
    private String webhookId = "";

    /**
     * The uninterruptible time (in seconds) before pinging {@link #getRoleId()}
     */
    private int timeBetweenPings = 60;

    /**
     * How to display the role ping when receiving a new mod mail message from a user
     */
    private RoleFormat roleFormat = RoleFormat.BELOW;

    public enum RoleFormat {
        /**
         * Above the first (or only) message
         */
        ABOVE,
        /**
         * Inline with the first (or only) message
         */
        INLINE,
        /**
         * After the last (or only) message
         */
        BELOW
    }
}