package net.hypixel.nerdbot.command;

import me.neiizun.lightdrop.automapping.AutoMapping;
import me.neiizun.lightdrop.command.Command;
import me.neiizun.lightdrop.command.CommandContext;
import net.hypixel.nerdbot.database.Database;

@AutoMapping
public class PublicCommands {

    @Command(name = "greenlit")
    public void greenlit(CommandContext context) {
        if (!Database.getInstance().isConnected()) {
            context.getMessage().reply("An error occurred!").queue();
            return;
        }

        int total = Database.getInstance().getGreenlitCollection().size();
        context.getMessage().reply("There are currently " + total + " greenlit suggestions.").queue();
    }

}
