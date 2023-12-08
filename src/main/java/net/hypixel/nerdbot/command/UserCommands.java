package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.slash.autocomplete.annotations.AutocompletionHandler;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.UserLanguage;
import net.hypixel.nerdbot.api.language.TranslationManager;
import net.hypixel.nerdbot.repository.DiscordUserRepository;

import java.util.List;

public class UserCommands extends ApplicationCommand {

    @JDASlashCommand(name = "language", description = "Change your language")
    public void setLanguage(GuildSlashEvent event, @AppOption(autocomplete = "languages") UserLanguage language) {
        event.deferReply(true).complete();

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            TranslationManager.getInstance().edit(event.getHook(), "database.not_connected");
            return;
        }

        DiscordUserRepository repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = repository.findById(event.getMember().getId());

        if (user == null) {
            TranslationManager.getInstance().edit(event.getHook(), "user.not_found");
            return;
        }

        user.setLanguage(language);
        TranslationManager.getInstance().edit(event.getHook(), user, "user.language_set", language.getName());
    }

    @AutocompletionHandler(name = "languages")
    public List<UserLanguage> getLanguages(CommandAutoCompleteInteractionEvent event) {
        return List.of(UserLanguage.VALUES);
    }
}
