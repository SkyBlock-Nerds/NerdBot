package net.hypixel.nerdbot.app.inject;

import dagger.BindsInstance;
import dagger.Component;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import net.dv8tion.jda.api.JDA;
import net.hypixel.nerdbot.app.reminder.ReminderScheduler;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.app.suggestion.SuggestionCache;
import net.hypixel.nerdbot.marmalade.storage.database.Database;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Builds the parts of the bot that have moved to constructor injection. Created once the bot is
 * ready; see {@code SkyBlockNerdsBot#onReady}.
 */
@Singleton
@Component(modules = {BotModule.class, StorageModule.class, CommandsModule.class, ListenersModule.class, FeaturesModule.class})
public interface BotComponent {

    @SlashCommandHandlers
    Set<Object> slashCommands();

    @EventListeners
    Set<Object> eventListeners();

    Map<Class<?>, Provider<BotFeature>> features();

    @AlwaysOnFeatures
    List<BotFeature> alwaysOnFeatures();

    ReminderScheduler reminderScheduler();

    SuggestionCache suggestionCache();

    ScheduledExecutorService scheduler();

    ExecutorService backgroundWorkPool();

    @ImageRendering
    ExecutorService imageRenderingPool();

    @Component.Factory
    interface Factory {
        BotComponent create(@BindsInstance JDA jda, @BindsInstance Database database, @BindsInstance BotState state,
                            @BindsInstance ResourcePackService resourcePacks);
    }
}
