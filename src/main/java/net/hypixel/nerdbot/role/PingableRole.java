package net.hypixel.nerdbot.role;

public record PingableRole(String name, String roleId) {

    public PingableRole {
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null");
        }

        if (roleId == null) {
            throw new IllegalArgumentException("Role ID cannot be null");
        }
    }
}
