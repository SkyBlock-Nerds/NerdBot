package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import net.dv8tion.jda.api.entities.Activity;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.bot.Bot;

public class ConfigCommand extends ApplicationCommand {

    @JDASlashCommand(name = "config", subcommand = "show", description = "View the currently loaded config", defaultLocked = true)
    public void showConfig(GuildSlashEvent event) {
        event.reply("```json\n" + NerdBotApp.getBot().getConfig().toString() + "```").setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "config", subcommand = "reload", description = "Reload the config file", defaultLocked = true)
    public void reloadConfig(GuildSlashEvent event) {
        Bot bot = NerdBotApp.getBot();
        bot.loadConfig();
        bot.getJDA().getPresence().setActivity(Activity.of(bot.getConfig().getActivityType(), bot.getConfig().getActivity()));
        event.reply("Reloaded the config file!").setEphemeral(true).queue();
    }
}
