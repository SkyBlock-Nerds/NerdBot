package net.hypixel.nerdbot.app.channel;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

import java.util.List;

/** Who can see a channel, judged from its own permission overrides. */
public final class ChannelAccess {

    private ChannelAccess() {
    }

    /** Whether @everyone is denied View Channel here. */
    public static boolean isHiddenFromEveryone(IPermissionContainer container) {
        PermissionOverride everyone = container.getPermissionOverride(container.getGuild().getPublicRole());
        return everyone != null && everyone.getDenied().contains(Permission.VIEW_CHANNEL);
    }

    /** The roles given View Channel by an override here. */
    public static List<Role> rolesAllowedToView(IPermissionContainer container) {
        return container.getPermissionOverrides().stream()
            .filter(override -> override.isRoleOverride() && override.getAllowed().contains(Permission.VIEW_CHANNEL))
            .map(PermissionOverride::getRole)
            .toList();
    }

    /** A short label for listings, e.g. "Public" or "Role-restricted: Staff". */
    public static String describe(GuildChannel channel) {
        IPermissionContainer container = channel.getPermissionContainer();
        if (!isHiddenFromEveryone(container)) {
            return "Public";
        }

        List<Role> allowed = rolesAllowedToView(container);
        return switch (allowed.size()) {
            case 0 -> "Private - No roles allowed";
            case 1 -> "Role-restricted: " + allowed.getFirst().getName();
            default -> "Role-restricted: " + allowed.size() + " roles";
        };
    }

    /** The longer breakdown /scan-channel shows. */
    public static String summary(GuildChannel channel) {
        IPermissionContainer container = channel.getPermissionContainer();
        if (!isHiddenFromEveryone(container)) {
            return "🌐 Publicly accessible";
        }

        List<Role> allowed = rolesAllowedToView(container);
        return "🔒 @everyone denied: `VIEW_CHANNEL`\n" + (allowed.isEmpty()
            ? "❌ No roles explicitly allowed"
            : "✅ Allowed roles: " + String.join(", ", allowed.stream().map(Role::getName).toList()));
    }
}
