package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.slash.autocomplete.annotations.AutocompletionHandler;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.language.UserLanguage;
import net.hypixel.nerdbot.api.language.TranslationManager;
import net.hypixel.nerdbot.repository.DiscordUserRepository;

import java.util.List;

public class UserCommands extends ApplicationCommand {

    public static final String SETTING_BASE_COMMAND = "setting";
    public static final String GEN_GROUP_COMMAND = "gen";

    @JDASlashCommand(name = SETTING_BASE_COMMAND, subcommand = "language", description = "Change your language")
    public void setLanguage(GuildSlashEvent event, @AppOption(autocomplete = "languages") UserLanguage language) {
        event.deferReply(true).complete();

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            TranslationManager.edit(event.getHook(), "database.not_connected");
            return;
        }

        DiscordUserRepository repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = repository.findById(event.getMember().getId());

        if (user == null) {
            TranslationManager.edit(event.getHook(), "generic.not_found", "User");
            return;
        }

        user.setLanguage(language);
        TranslationManager.edit(event.getHook(), user, "commands.language.language_set", language.getName());
    }

    @AutocompletionHandler(name = "languages")
    public List<UserLanguage> getLanguages(CommandAutoCompleteInteractionEvent event) {
        return List.of(UserLanguage.VALUES);
    }

    @JDASlashCommand(name = SETTING_BASE_COMMAND, group = GEN_GROUP_COMMAND, subcommand = "autohide", description = "Change if your gen commands are automatically hidden")
    public void setHidePreference(GuildSlashEvent event, @AppOption() boolean autohide) {
        event.deferReply(true).complete();

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            TranslationManager.edit(event.getHook(), "database.not_connected");
            return;
        }

        DiscordUserRepository repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = repository.findById(event.getMember().getId());

        if (user == null) {
            TranslationManager.edit(event.getHook(), "generic.not_found", "User");
            return;
        }

        user.setAutoHideGenCommands(autohide);
        TranslationManager.edit(event.getHook(), user, "commands.auto_hide_preference.preference_set_" + Boolean.toString(autohide));
    }
}