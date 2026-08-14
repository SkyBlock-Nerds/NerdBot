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
import net.hypixel.nerdbot.app.drive.DriveLogEmbeds;
import net.hypixel.nerdbot.app.drive.DrivePermissionService;
import net.hypixel.nerdbot.app.drive.DriveSyncSweep;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.drive.DriveGrant;
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
    private static final String NOT_CONFIGURED_MESSAGE = "Drive access syncing is not configured on this server.";

    private static DiscordUserRepository repository() {
        return BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
    }

    @SlashCommand(name = "drive", subcommand = "link", description = "Link your Google account email for shared Drive access", guildOnly = true)
    public void driveLink(SlashCommandInteractionEvent event) {
        if (SkyBlockNerdsBot.drivePermissionService().isEmpty()) {
            event.reply(NOT_CONFIGURED_MESSAGE).setEphemeral(true).queue();
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
            event.reply(NOT_CONFIGURED_MESSAGE).setEphemeral(true).queue();
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
            case INVALID_EMAIL -> event.getHook().editOriginal("That doesn't look like a valid email address, so nothing was saved.").queue();
            case DUPLICATE_EMAIL -> {
                log.warn("Member {} attempted to link an email already linked to another member", event.getUser().getId());
                event.getHook().editOriginal("That email is already linked to another member. If it's yours, ask a moderator for help.").queue();
            }
            case LINKED -> {
                repository.cacheObject(user);
                repository.saveToDatabaseAsync(user);
                DriveLogEmbeds.postAccessChange(service.get(), user.getDiscordId(),
                    result.outcome().grantedFolders(), result.outcome().revokedFolders());
                int granted = result.outcome().grantedFolders().size();
                int failedCount = result.outcome().failedFolders().size();
                long rateLimited = result.outcome().failedFolders().stream()
                    .filter(failure -> failure.reason() != null && failure.reason().toLowerCase().contains("ratelimit"))
                    .count();
                long rejectedByGoogle = result.outcome().failedFolders().stream()
                    .filter(failure -> failure.statusCode() >= 400 && failure.statusCode() < 500)
                    .count() - rateLimited;

                String summary;
                if (!result.outcome().hasFailures()) {
                    summary = "Linked! You now have access to " + granted + " folder(s).";
                } else if (rateLimited > 0) {
                    summary = "Linked! " + (granted > 0 ? granted + " folder(s) granted now and " : "Your ") + rateLimited
                        + " folder(s) queued while Google rate limits us; they'll arrive within the hour.";
                } else if (rejectedByGoogle > 0) {
                    summary = "Google rejected that address for " + rejectedByGoogle + " folder(s). Make sure it's a Google account email.";
                } else {
                    summary = "Linked! " + failedCount + " folder(s) couldn't be granted right now; retrying within the hour.";
                }
                event.getHook().editOriginal(summary).queue();
            }
        }
    }

    @SlashCommand(name = "drive", subcommand = "unlink", description = "Remove your linked email and revoke your Drive access", guildOnly = true)
    public void driveUnlink(SlashCommandInteractionEvent event) {
        Optional<DrivePermissionService> service = SkyBlockNerdsBot.drivePermissionService();
        if (service.isEmpty()) {
            event.reply(NOT_CONFIGURED_MESSAGE).setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        DiscordUserRepository repository = repository();
        DiscordUser user = repository.findById(event.getUser().getId()).toOptional().orElse(null);

        DriveLinkWorkflow workflow = new DriveLinkWorkflow(service.get(), SkyBlockNerdsBot.config().getGoogleDriveConfig());
        List<String> heldFolders = user == null || user.getDriveAccess() == null
            ? List.of()
            : user.getDriveAccess().getGrants().stream().map(DriveGrant::folderId).toList();
        if (user == null || !workflow.unlink(user)) {
            event.getHook().editOriginal("You don't have a linked email.").queue();
            return;
        }

        repository.cacheObject(user);
        // workflow.unlink() always nulls driveAccess on success; a $set save can't clear it from Mongo, so unset the field directly.
        repository.unsetField(user.getDiscordId(), "driveAccess");
        DriveLogEmbeds.postAccessChange(service.get(), user.getDiscordId(), List.of(), heldFolders);
        event.getHook().editOriginal("Unlinked. Your Drive access has been revoked and your email deleted.").queue();
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
        event.getHook().editOriginal("Email linked. Folder access: " + grants
            + "\nLast synced: <t:" + user.getDriveAccess().getLastSyncedAt() / 1000 + ":R>").queue();
    }

    @SlashCommand(name = "drive", subcommand = "sync", description = "Force a full Drive permission reconcile now", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void driveSync(SlashCommandInteractionEvent event) {
        if (SkyBlockNerdsBot.drivePermissionService().isEmpty()) {
            event.reply(NOT_CONFIGURED_MESSAGE).setEphemeral(true).queue();
            return;
        }

        log.info("Member {} invoked /drive sync", event.getUser().getId());
        event.deferReply(true).queue();
        DriveSyncSweep.Result result = new DriveSyncSweep().run();
        event.getHook().editOriginal("Reconcile finished: " + result.summary()).queue();
    }
}
