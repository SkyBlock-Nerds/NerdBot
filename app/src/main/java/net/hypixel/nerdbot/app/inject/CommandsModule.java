package net.hypixel.nerdbot.app.inject;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import net.hypixel.nerdbot.app.command.AdminCommands;
import net.hypixel.nerdbot.app.command.BadgeCommands;
import net.hypixel.nerdbot.app.command.ChannelCommands;
import net.hypixel.nerdbot.app.command.DriveCommands;
import net.hypixel.nerdbot.app.command.ExportCommands;
import net.hypixel.nerdbot.app.command.GeneratorCommands;
import net.hypixel.nerdbot.app.command.InfoCommands;
import net.hypixel.nerdbot.app.command.PackCommands;
import net.hypixel.nerdbot.app.command.PostCommands;
import net.hypixel.nerdbot.app.command.ProfileCommands;
import net.hypixel.nerdbot.app.command.PunishmentCommands;
import net.hypixel.nerdbot.app.command.ReminderCommands;
import net.hypixel.nerdbot.app.command.RoleCommands;
import net.hypixel.nerdbot.app.command.SuggestionCommands;
import net.hypixel.nerdbot.app.profile.MojangProfileLookup;
import net.hypixel.nerdbot.app.profile.ProfileEmbeds;
import net.hypixel.nerdbot.app.profile.ProfileLinker;

/**
 * Every slash command class the bot registers. Commands that haven't moved to constructor injection
 * are still built with {@code new}; when one moves, its provider here becomes a {@code @Binds}.
 */
@Module
public interface CommandsModule {

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object punishmentCommands(PunishmentCommands commands);

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object reminderCommands(ReminderCommands commands);

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object suggestionCommands(SuggestionCommands commands);

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object exportCommands(ExportCommands commands);

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object infoCommands(InfoCommands commands);

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object channelCommands(ChannelCommands commands);

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object roleCommands(RoleCommands commands);

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object driveCommands(DriveCommands commands);

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object postCommands(PostCommands commands);

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object packCommands(PackCommands commands);

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object badgeCommands(BadgeCommands commands);

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object generatorCommands(GeneratorCommands commands);

    @Provides
    @IntoSet
    @SlashCommandHandlers
    static Object adminCommands(MojangProfileLookup profileLookup, ProfileLinker profileLinker, ProfileEmbeds profileEmbeds) {
        return new AdminCommands(profileLookup, profileLinker, profileEmbeds);
    }

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object profileCommands(ProfileCommands commands);
}
