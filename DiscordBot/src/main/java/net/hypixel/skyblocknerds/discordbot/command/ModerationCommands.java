package net.hypixel.skyblocknerds.discordbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.components.InteractionConstraints;
import com.freya02.botcommands.api.pagination.menu.Menu;
import com.freya02.botcommands.api.pagination.menu.MenuBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.hypixel.skyblocknerds.database.objects.user.warning.WarningEntry;
import net.hypixel.skyblocknerds.database.repository.RepositoryManager;
import net.hypixel.skyblocknerds.database.repository.impl.DiscordUserRepository;
import net.hypixel.skyblocknerds.utilities.discord.DiscordTimestamp;

public class ModerationCommands extends ApplicationCommand {

    private final DiscordUserRepository discordUserRepository;

    public ModerationCommands() {
        this.discordUserRepository = RepositoryManager.getInstance().getRepository(DiscordUserRepository.class);
    }

    @JDASlashCommand(name = "warn", description = "Issue an official warning to a member", defaultLocked = true)
    public void warnMember(GuildSlashEvent event, @AppOption(description = "The member to warn") Member member, @AppOption(description = "The reason for the warning") String reason) {
        event.deferReply(true).complete();

        discordUserRepository.findById(member.getId()).ifPresentOrElse(discordUser -> {
            discordUser.issueWarning(event.getMember().getId(), reason);
            event.getHook().editOriginal("Warned " + member.getAsMention() + " for `" + reason + "`").queue();
            // TODO log the warning in a channel
        }, () -> event.reply("Couldn't find that member in the database!").queue());
    }

    @JDASlashCommand(name = "warnings", description = "View the warnings of a member", defaultLocked = true)
    public void viewWarnings(GuildSlashEvent event, @AppOption(description = "The member to view the warnings of") Member member) {
        discordUserRepository.findById(member.getId()).ifPresentOrElse(discordUser -> {
            if (discordUser.getWarnings().isEmpty()) {
                event.reply(member.getAsMention() + " has no warnings!").setEphemeral(true).queue();
            } else {
                Menu<WarningEntry> paginator = new MenuBuilder<>(discordUser.getWarnings())
                    .setConstraints(InteractionConstraints.ofUsers(event.getUser()))
                    .useDeleteButton(false)
                    .setTransformer(warning -> String.format("Issued by <@%s> at %s for `%s`", warning.getIssuerId(), DiscordTimestamp.toLongDateTime(warning.getTimestamp()), warning.getReason()))
                    .setMaxEntriesPerPage(10)
                    .build();

                event.reply(MessageCreateData.fromEditData(paginator.get()))
                    .setEphemeral(true)
                    .queue();
            }
        }, () -> event.reply("Could not find that member in the database!").setEphemeral(true).queue());
    }
}
