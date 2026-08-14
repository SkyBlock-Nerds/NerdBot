package net.hypixel.nerdbot.app.command;

import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashModalHandler;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.app.drive.DriveLinkWorkflow;
import net.hypixel.nerdbot.app.drive.DrivePermissionService;
import net.hypixel.nerdbot.app.drive.DriveSyncSweep;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Members link a Google email through a modal (the address never appears in
 * chat) and the bot mirrors their roles onto shared-Drive folder permissions.
 * All responses are ephemeral.
 */
@Slf4j
public class DriveCommands {

    private static final String MODAL_ID_PREFIX = "drive-email-modal";

    private static DiscordUserRepository repository() {
        return BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
    }

    @SlashCommand(name = "drive", subcommand = "link", description = "Link your Google account email for shared Drive access", guildOnly = true)
    public void driveLink(SlashCommandInteractionEvent event) {
        if (SkyBlockNerdsBot.drivePermissionService().isEmpty()) {
            event.reply("Drive access syncing is not configured on this server.").setEphemeral(true).queue();
            return;
        }

        TextInput emailInput = TextInput.create("email", "Google account email", TextInputStyle.SHORT)
            .setPlaceholder("you@gmail.com")
            .setRequiredRange(3, 254)
            .build();

        Modal modal = Modal.create(MODAL_ID_PREFIX + "-" + event.getUser().getId(), "Link Google Drive access")
            .addComponents(ActionRow.of(emailInput))
            .build();
        event.replyModal(modal).queue();
    }

    @SlashModalHandler(id = MODAL_ID_PREFIX, patterns = {MODAL_ID_PREFIX + "-*"})
    public void handleEmailModal(ModalInteractionEvent event) {
        String ownerId = event.getModalId().substring(MODAL_ID_PREFIX.length() + 1);
        if (!event.getUser().getId().equals(ownerId)) {
            event.reply("This modal belongs to someone else!").setEphemeral(true).queue();
            return;
        }

        Optional<DrivePermissionService> service = SkyBlockNerdsBot.drivePermissionService();
        if (service.isEmpty() || event.getMember() == null) {
            event.reply("Drive access syncing is not configured on this server.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        DiscordUserRepository repository = repository();
        DiscordUser user = repository.findOrCreateById(event.getUser().getId(), event.getUser().getId());
        List<String> roleIds = event.getMember().getRoles().stream().map(role -> role.getId()).toList();

        DriveLinkWorkflow workflow = new DriveLinkWorkflow(service.get(), SkyBlockNerdsBot.config().getGoogleDriveConfig());
        DriveLinkWorkflow.LinkResult result = workflow.link(
            user, event.getValue("email").getAsString(), roleIds, repository.getAllDocuments());

        switch (result.status()) {
            case INVALID_EMAIL -> event.getHook().editOriginal("That doesn't look like a valid email address — nothing was saved.").queue();
            case DUPLICATE_EMAIL -> event.getHook().editOriginal("That email is already linked to another member. If it's yours, ask a moderator for help.").queue();
            case LINKED -> {
                repository.cacheObject(user);
                repository.saveToDatabaseAsync(user);
                String summary = "Linked! You now have access to " + result.outcome().grantedFolders().size() + " folder(s).";
                if (result.outcome().hasFailures()) {
                    summary += " " + result.outcome().failedFolders().size() + " folder(s) couldn't be granted right now — this retries automatically within the hour.";
                }
                event.getHook().editOriginal(summary).queue();
            }
        }
    }

    @SlashCommand(name = "drive", subcommand = "unlink", description = "Remove your linked email and revoke your Drive access", guildOnly = true)
    public void driveUnlink(SlashCommandInteractionEvent event) {
        Optional<DrivePermissionService> service = SkyBlockNerdsBot.drivePermissionService();
        if (service.isEmpty()) {
            event.reply("Drive access syncing is not configured on this server.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        DiscordUserRepository repository = repository();
        DiscordUser user = repository.findById(event.getUser().getId()).toOptional().orElse(null);

        DriveLinkWorkflow workflow = new DriveLinkWorkflow(service.get(), SkyBlockNerdsBot.config().getGoogleDriveConfig());
        if (user == null || !workflow.unlink(user)) {
            event.getHook().editOriginal("You don't have a linked email.").queue();
            return;
        }

        repository.cacheObject(user);
        repository.saveToDatabaseAsync(user);
        event.getHook().editOriginal("Unlinked — your Drive access has been revoked and your email deleted.").queue();
    }

    @SlashCommand(name = "drive", subcommand = "status", description = "Show your current Drive link and folder access", guildOnly = true)
    public void driveStatus(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        DiscordUser user = repository().findById(event.getUser().getId()).toOptional().orElse(null);

        if (user == null || user.getDriveAccess() == null) {
            event.getHook().editOriginal("No email linked. Use `/drive link` to get Drive access.").queue();
            return;
        }

        String grants = user.getDriveAccess().getGrants().isEmpty()
            ? "none yet"
            : user.getDriveAccess().getGrants().stream()
                .map(grant -> grant.folderId() + " (" + grant.accessLevel().toLowerCase() + ")")
                .collect(Collectors.joining(", "));
        event.getHook().editOriginal("Email linked ✔ — folder access: " + grants
            + "\nLast synced: <t:" + user.getDriveAccess().getLastSyncedAt() / 1000 + ":R>").queue();
    }

    @SlashCommand(name = "drive", subcommand = "sync", description = "Force a full Drive permission reconcile now", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void driveSync(SlashCommandInteractionEvent event) {
        if (SkyBlockNerdsBot.drivePermissionService().isEmpty()) {
            event.reply("Drive access syncing is not configured on this server.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        DriveSyncSweep.Result result = new DriveSyncSweep().run();
        event.getHook().editOriginal("Reconcile finished: " + result.summary()).queue();
    }
}
