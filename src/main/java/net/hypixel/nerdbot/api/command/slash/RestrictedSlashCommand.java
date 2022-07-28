package net.hypixel.nerdbot.api.command.slash;

import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;

public interface RestrictedSlashCommand {

    DefaultMemberPermissions getPermission();

}
