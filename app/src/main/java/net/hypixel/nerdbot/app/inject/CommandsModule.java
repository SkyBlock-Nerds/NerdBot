package net.hypixel.nerdbot.app.inject;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoSet;
import net.hypixel.nerdbot.app.command.BadgeCommands;
import net.hypixel.nerdbot.app.command.CacheCommands;
import net.hypixel.nerdbot.app.command.ChannelCommands;
import net.hypixel.nerdbot.app.command.ConfigCommands;
import net.hypixel.nerdbot.app.command.CurateCommands;
import net.hypixel.nerdbot.app.command.DriveCommands;
import net.hypixel.nerdbot.app.command.ExportCommands;
import net.hypixel.nerdbot.app.command.GeneratorCommands;
import net.hypixel.nerdbot.app.command.InfoCommands;
import net.hypixel.nerdbot.app.command.InviteCommands;
import net.hypixel.nerdbot.app.command.NominationCommands;
import net.hypixel.nerdbot.app.command.PackCommands;
import net.hypixel.nerdbot.app.command.PostCommands;
import net.hypixel.nerdbot.app.command.ProfileCommands;
import net.hypixel.nerdbot.app.command.PunishmentCommands;
import net.hypixel.nerdbot.app.command.ReminderCommands;
import net.hypixel.nerdbot.app.command.RestrictedChannelCommands;
import net.hypixel.nerdbot.app.command.RoleCommands;
import net.hypixel.nerdbot.app.command.SuggestionCommands;
import net.hypixel.nerdbot.app.command.TagTransferCommands;
import net.hypixel.nerdbot.app.command.UserCommands;

/**
 * Every slash command class the bot registers.
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

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object profileCommands(ProfileCommands commands);

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object cacheCommands(CacheCommands commands);

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object configCommands(ConfigCommands commands);

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object curateCommands(CurateCommands commands);

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object inviteCommands(InviteCommands commands);

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object nominationCommands(NominationCommands commands);

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object restrictedChannelCommands(RestrictedChannelCommands commands);

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object tagTransferCommands(TagTransferCommands commands);

    @Binds
    @IntoSet
    @SlashCommandHandlers
    Object userCommands(UserCommands commands);
}
