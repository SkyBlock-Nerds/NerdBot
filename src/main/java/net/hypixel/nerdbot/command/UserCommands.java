package net.hypixel.nerdbot.command;

import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.repository.DiscordUserRepository;

public class UserCommands {

    public static final String SETTING_BASE_COMMAND = "setting";
    public static final String GEN_GROUP_COMMAND = "gen";

    @SlashCommand(name = SETTING_BASE_COMMAND, group = GEN_GROUP_COMMAND, subcommand = "autohide", description = "Change if your gen commands are automatically hidden")
    public void setHidePreference(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Whether to automatically hide your generator commands") boolean autohide
    ) {
        event.deferReply(true).complete();

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to the database! Please try again later!").queue();
            return;
        }

        DiscordUserRepository repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = repository.findById(event.getMember().getId());

        if (user == null) {
            event.getHook().editOriginal("User not found").queue();
            return;
        }

        user.setAutoHideGenCommands(autohide);
        event.getHook().editOriginal("Your preference to automatically hide generated images is now **" + autohide + "**").queue();
    }
}
