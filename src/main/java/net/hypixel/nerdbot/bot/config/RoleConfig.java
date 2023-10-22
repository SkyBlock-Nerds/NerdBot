package net.hypixel.nerdbot.bot.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.dv8tion.jda.api.entities.Role;
import net.hypixel.nerdbot.role.PingableRole;

@Getter
@Setter
@ToString
public class RoleConfig {

    /**
     * The {@link Role} ID of the Bot Manager role
     */
    private String botManagerRoleId = "";

    /**
     * The {@link Role} ID of the New Member role
     */
    private String newMemberRoleId = "";

    /**
     * The {@link Role} ID of the Limbo role
     */
    private String limboRoleId = "";

    /**
     * A list of {@link PingableRole}s used for announcements etc.
     */
    private PingableRole[] pingableRoles = {};
}
