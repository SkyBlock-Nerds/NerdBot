package net.hypixel.nerdbot.app.role;

import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies a member's current Discord role ids to their stored user document.
 * Kept pure so listener and reconcile paths share one tested implementation.
 */
public final class RoleIdSync {

    private RoleIdSync() {
    }

    /**
     * @return true if the stored role ids changed and the user needs saving
     */
    public static boolean applyRoleIds(DiscordUser user, List<String> currentRoleIds) {
        List<String> normalized = new ArrayList<>(currentRoleIds);
        if (normalized.equals(user.getRoleIds())) {
            return false;
        }
        user.setRoleIds(normalized);
        return true;
    }
}
