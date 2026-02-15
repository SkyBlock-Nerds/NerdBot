package net.hypixel.nerdbot.app.nomination;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.hypixel.nerdbot.discord.role.RoleManager;
import net.hypixel.nerdbot.discord.config.RoleConfig;

/**
 * Resolves a human-readable nomination type string (e.g. "New Member -> Member")
 * using the configured ordered promotion path in {@link RoleConfig#getPromotionTierRoleIds()}.
 * Returns null if no next tier exists for the member's current highest role.
 */
@UtilityClass
public class NominationTypeResolver {

    public static String resolve(Member member, RoleConfig roleConfig) {
        Role highestRole = RoleManager.getHighestRole(member);
        String[] promotionPath = roleConfig.getPromotionTierRoleIds();

        if (highestRole == null || promotionPath == null || promotionPath.length == 0) {
            return null;
        }

        int currentRoleIndex = -1;
        for (int i = 0; i < promotionPath.length; i++) {
            String roleId = promotionPath[i];
            if (roleId != null && roleId.equalsIgnoreCase(highestRole.getId())) {
                currentRoleIndex = i;
                break;
            }
        }

        if (currentRoleIndex < 0 || currentRoleIndex + 1 >= promotionPath.length) {
            return null;
        }

        String targetRoleId = promotionPath[currentRoleIndex + 1];
        Role targetRole = member.getGuild().getRoleById(targetRoleId);
        String targetName = targetRole != null ? targetRole.getName() : targetRoleId;
        String sourceName = highestRole.getName() != null ? highestRole.getName() : highestRole.getId();

        return sourceName + " -> " + targetName;
    }
}