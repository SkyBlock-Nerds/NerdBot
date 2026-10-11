package net.hypixel.nerdbot.app.inject;

import dagger.BindsInstance;
import dagger.Component;
import net.hypixel.nerdbot.marmalade.storage.database.Database;

import java.util.Set;

/**
 * Builds the parts of the bot that have moved to constructor injection. Created once the bot is
 * ready; see {@code SkyBlockNerdsBot#onReady}.
 */
@Component(modules = {BotModule.class, StorageModule.class, CommandsModule.class})
public interface BotComponent {

    @SlashCommandHandlers
    Set<Object> slashCommands();

    @Component.Factory
    interface Factory {
        BotComponent create(@BindsInstance Database database);
    }
}
