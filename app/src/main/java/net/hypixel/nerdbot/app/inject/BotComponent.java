package net.hypixel.nerdbot.app.inject;

import dagger.BindsInstance;
import dagger.Component;
import jakarta.inject.Singleton;
import net.dv8tion.jda.api.JDA;
import net.hypixel.nerdbot.app.reminder.ReminderScheduler;
import net.hypixel.nerdbot.app.suggestion.SuggestionCache;
import net.hypixel.nerdbot.marmalade.storage.database.Database;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Builds the parts of the bot that have moved to constructor injection. Created once the bot is
 * ready; see {@code SkyBlockNerdsBot#onReady}.
 */
@Singleton
@Component(modules = {BotModule.class, StorageModule.class, CommandsModule.class})
public interface BotComponent {

    @SlashCommandHandlers
    Set<Object> slashCommands();

    ReminderScheduler reminderScheduler();

    SuggestionCache suggestionCache();

    ScheduledExecutorService scheduler();

    ExecutorService backgroundWorkPool();

    @Component.Factory
    interface Factory {
        BotComponent create(@BindsInstance JDA jda, @BindsInstance Database database, @BindsInstance BotState state);
    }
}
