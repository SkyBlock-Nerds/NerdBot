package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.curator.Curator;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.GreenlitMessage;
import net.hypixel.nerdbot.curator.ForumChannelCurator;

import java.util.List;

public class CurateSlashCommand extends ApplicationCommand {

    @JDASlashCommand(name = "curate", description = "Manually run the curation process", defaultLocked = true)
    public void curate(GuildSlashEvent event, @AppOption ForumChannel channel, @Optional @AppOption(description = "Run the curator without greenlighting suggestions") boolean readOnly) {
        if (!Database.getInstance().isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            NerdBotApp.LOGGER.error("Couldn't connect to the database!");
            return;
        }

        Curator<ForumChannel> forumChannelCurator = new ForumChannelCurator(readOnly);
        NerdBotApp.EXECUTOR_SERVICE.submit(() -> {
            event.deferReply(true).queue();
            List<GreenlitMessage> output = forumChannelCurator.curate(channel);
            if (output.isEmpty()) {
                event.getHook().editOriginal("No suggestions were greenlit!").queue();
            } else {
                event.getHook().editOriginal("Greenlit " + output.size() + " suggestions in " + (forumChannelCurator.getEndTime() - forumChannelCurator.getStartTime()) + "ms!").queue();
            }
        });
    }
}
