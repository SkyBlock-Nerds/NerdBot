package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.requests.restaction.InviteAction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Log4j2
public class AdminCommands extends ApplicationCommand {

    @JDASlashCommand(name = "createinvites", description = "Generate a bunch of invites", defaultLocked = true)
    public void showChannelGroups(GuildSlashEvent event, @AppOption int amount) {
        List<Invite> invites = new ArrayList<>(amount);

        event.deferReply(true).queue();

        for (int i = 0; i < amount; i++) {
            InviteAction action = event.getChannel().asTextChannel()
                    .createInvite()
                    .setUnique(true)
                    .setMaxAge(7L, TimeUnit.DAYS)
                    .setMaxUses(1);

            Invite invite = action.complete();
            invites.add(invite);
            log.info("Generated new temporary invite '" + invite.getUrl() + "'");
        }

        StringBuilder stringBuilder = new StringBuilder("Generated invites (");
        stringBuilder.append(invites.size()).append("):\n");

        invites.forEach(invite -> {
            stringBuilder.append(invite.getUrl()).append("\n");
        });

        event.getHook().editOriginal(stringBuilder.toString()).queue();
    }
}
