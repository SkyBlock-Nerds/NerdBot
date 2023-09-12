package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import net.dv8tion.jda.api.entities.Role;
import net.hypixel.nerdbot.util.Util;

public class RoleCommands extends ApplicationCommand {

    @JDASlashCommand(name = "role", subcommand = "tag-discussion", description = "Toggle your participation in the Discord Tag rework discussion")
    public void tagDiscussionRoleToggle(GuildSlashEvent event) {
        Role role = Util.getRole("Discord Tag Discussion");

        if (role == null) {
            event.reply("I couldn't find the role!").setEphemeral(true).queue();
            return;
        }

        if (event.getMember().getRoles().contains(role)) {
            event.getGuild().removeRoleFromMember(event.getMember(), role).queue();
            event.reply("You will no longer be pinged by this role!").setEphemeral(true).queue();
        } else {
            event.getGuild().addRoleToMember(event.getMember(), role).queue();
            event.reply("You will now be pinged by this role!").setEphemeral(true).queue();
        }
    }
}
