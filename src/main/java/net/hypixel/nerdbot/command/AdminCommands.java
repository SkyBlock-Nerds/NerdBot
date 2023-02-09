package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.InviteAction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Log4j2
public class AdminCommands extends ApplicationCommand {

    @JDASlashCommand(name = "invites", subcommand = "create", description = "Generate a bunch of invites for a specific channel", defaultLocked = true)
    public void createInvites(GuildSlashEvent event, @AppOption int amount, @AppOption TextChannel channel) {
        List<Invite> invites = new ArrayList<>(amount);

        event.deferReply(true).queue();

        for (int i = 0; i < amount; i++) {
            InviteAction action = channel.createInvite()
                    .setUnique(true)
                    .setMaxAge(7L, TimeUnit.DAYS)
                    .setMaxUses(1);

            Invite invite = action.complete();
            invites.add(invite);
            log.info("Generated new temporary invite '" + invite.getUrl() + "' for channel " + channel.getName() + " by " + event.getUser().getAsTag());
        }

        StringBuilder stringBuilder = new StringBuilder("Generated invites (");
        stringBuilder.append(invites.size()).append("):\n");

        invites.forEach(invite -> {
            stringBuilder.append(invite.getUrl()).append("\n");
        });

        event.getHook().editOriginal(stringBuilder.toString()).queue();
    }

    @JDASlashCommand(name = "invites", subcommand = "delete", description = "Delete all active invites", defaultLocked = true)
    public void deleteInvites(GuildSlashEvent event) {
        event.deferReply(true).queue();

        List<Invite> invites = event.getGuild().retrieveInvites().complete();
        invites.forEach(invite -> {
            invite.delete().complete();
            log.info(event.getUser().getAsTag() + " deleted invite " + invite.getUrl());
        });

        event.getHook().editOriginal("Deleted " + invites.size() + " invites").queue();
    }
}
