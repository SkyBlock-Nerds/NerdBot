package net.hypixel.nerdbot.app.command;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashModalHandler;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.hypixel.nerdbot.app.channel.LogChannel;
import net.hypixel.nerdbot.app.command.util.CommandReplies;
import net.hypixel.nerdbot.app.command.util.PanelId;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.drive.DriveLinkWorkflow;
import net.hypixel.nerdbot.app.drive.DriveLogEmbeds;
import net.hypixel.nerdbot.app.drive.DrivePermissionService;
import net.hypixel.nerdbot.app.drive.DriveSyncSweep;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.drive.DriveGrant;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Members link a Google email through a modal (the address never appears in
 * chat) and the bot mirrors their roles onto shared-Drive folder permissions.
 * All responses are ephemeral.
 */
@Slf4j
public class DriveCommands {

    static final String MODAL_PREFIX = "drive-email-modal";
    static final PanelId MODAL = PanelId.of(MODAL_PREFIX, 1, 0);
    static final String NOT_CONFIGURED_MESSAGE = "Drive access syncing is not configured on this server.";
    static final String NOT_YOUR_MODAL = "This modal belongs to someone else!";

    private final Provider<Optional<DrivePermissionService>> driveService;
    private final Provider<NerdBotConfig> config;
    private final DiscordUserStore users;
    private final LogChannel logChannel;
    private final Provider<DriveSyncSweep> syncSweep;

    @Inject
    public DriveCommands(Provider<Optional<DrivePermissionService>> driveService, Provider<NerdBotConfig> config,
                         DiscordUserStore users, LogChannel logChannel, Provider<DriveSyncSweep> syncSweep) {
        this.driveService = driveService;
        this.config = config;
        this.users = users;
        this.logChannel = logChannel;
        this.syncSweep = syncSweep;
    }

    @SlashCommand(name = "drive", subcommand = "link", description = "Link your Google account email for shared Drive access", guildOnly = true)
    public void driveLink(SlashCommandInteractionEvent event) {
        openLinkModal(event);
    }

    @SlashModalHandler(id = MODAL_PREFIX, patterns = {MODAL_PREFIX + "-*"})
    public void handleEmailModal(ModalInteractionEvent event) {
        submitEmail(event);
    }

    @SlashCommand(name = "drive", subcommand = "unlink", description = "Remove your linked email and revoke your Drive access", guildOnly = true)
    public void driveUnlink(SlashCommandInteractionEvent event) {
        unlink(event);
    }

    @SlashCommand(name = "drive", subcommand = "status", description = "Show your current Drive link and folder access", guildOnly = true)
    public void driveStatus(SlashCommandInteractionEvent event) {
        showStatus(event);
    }

    @SlashCommand(name = "drive", subcommand = "sync", description = "Force a full Drive permission reconcile now", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void driveSync(SlashCommandInteractionEvent event) {
        sync(event);
    }

    <T extends IReplyCallback & IModalCallback> void openLinkModal(T interaction) {
        if (driveService.get().isEmpty()) {
            CommandReplies.replyPrivately(interaction, NOT_CONFIGURED_MESSAGE);
            return;
        }

        TextInput emailInput = TextInput.create("email", "Google account email", TextInputStyle.SHORT)
            .setPlaceholder("you@gmail.com")
            .setRequiredRange(3, 254)
            .build();

        Modal modal = Modal.create(MODAL.build(interaction.getUser().getId()), "Link Google Drive access")
            .addComponents(ActionRow.of(emailInput))
            .build();
        interaction.replyModal(modal).queue();
    }

    void submitEmail(ModalInteraction interaction) {
        if (MODAL.parseFor(interaction, interaction.getModalId(), NOT_YOUR_MODAL).isEmpty()) {
            return;
        }

        Optional<DrivePermissionService> service = driveService.get();
        ModalMapping email = interaction.getValue("email");
        if (service.isEmpty() || interaction.getMember() == null || email == null) {
            CommandReplies.replyPrivately(interaction, NOT_CONFIGURED_MESSAGE);
            return;
        }

        InteractionHook hook = CommandReplies.deferPrivately(interaction);
        String memberId = interaction.getUser().getId();
        DiscordUser user = users.require(memberId);
        List<String> roleIds = interaction.getMember().getRoles().stream().map(ISnowflake::getId).toList();

        DriveLinkWorkflow.LinkResult result = workflow(service.get())
            .link(user, email.getAsString(), roleIds, users.getAllFromDatabase());

        switch (result.status()) {
            case INVALID_EMAIL -> hook.editOriginal("That doesn't look like a valid email address, so nothing was saved.").queue();
            case DUPLICATE_EMAIL -> {
                log.warn("Member {} attempted to link an email already linked to another member", memberId);
                hook.editOriginal("That email is already linked to another member. If it's yours, ask a moderator for help.").queue();
            }
            case LINKED -> {
                DriveLogEmbeds.postAccessChange(logChannel, service.get(), memberId,
                    result.outcome().grantedFolders(), result.outcome().revokedFolders());
                CommandReplies.finish(users.save(user), hook, "save the Drive link for " + memberId,
                    "Your Drive access was updated, but saving the link failed. Please run `/drive link` again.",
                    saved -> hook.editOriginal(linkSummary(result.outcome())).queue());
            }
        }
    }

    void unlink(IReplyCallback interaction) {
        Optional<DrivePermissionService> service = driveService.get();
        if (service.isEmpty()) {
            CommandReplies.replyPrivately(interaction, NOT_CONFIGURED_MESSAGE);
            return;
        }

        InteractionHook hook = CommandReplies.deferPrivately(interaction);
        DiscordUser user = users.require(interaction.getUser().getId());
        List<String> heldFolders = user.getDriveAccess() == null
            ? List.of()
            : user.getDriveAccess().getGrants().stream().map(DriveGrant::folderId).toList();

        if (!workflow(service.get()).unlink(user)) {
            hook.editOriginal("You don't have a linked email.").queue();
            return;
        }

        users.clearDriveAccess(user);
        DriveLogEmbeds.postAccessChange(logChannel, service.get(), user.getDiscordId(), List.of(), heldFolders);
        hook.editOriginal("Unlinked. Your Drive access has been revoked and your email deleted.").queue();
    }

    void showStatus(IReplyCallback interaction) {
        InteractionHook hook = CommandReplies.deferPrivately(interaction);
        DiscordUser user = users.require(interaction.getUser().getId());

        if (user.getDriveAccess() == null) {
            hook.editOriginal("No email linked. Use `/drive link` to get Drive access.").queue();
            return;
        }

        String grants = user.getDriveAccess().getGrants().isEmpty()
            ? "none yet"
            : user.getDriveAccess().getGrants().stream()
                .map(grant -> grant.folderId() + " (" + grant.accessLevel().toLowerCase(Locale.ROOT) + ")")
                .collect(Collectors.joining(", "));
        hook.editOriginal("Email linked. Folder access: " + grants
            + "\nLast synced: <t:" + user.getDriveAccess().getLastSyncedAt() / 1000 + ":R>").queue();
    }

    void sync(IReplyCallback interaction) {
        if (driveService.get().isEmpty()) {
            CommandReplies.replyPrivately(interaction, NOT_CONFIGURED_MESSAGE);
            return;
        }

        log.info("Member {} invoked /drive sync", interaction.getUser().getId());
        InteractionHook hook = CommandReplies.deferPrivately(interaction);
        DriveSyncSweep.Result result = syncSweep.get().run();
        hook.editOriginal("Reconcile finished: " + result.summary()).queue();
    }

    static String linkSummary(DrivePermissionService.SyncOutcome outcome) {
        int granted = outcome.grantedFolders().size();
        if (!outcome.hasFailures()) {
            return "Linked! You now have access to " + granted + " folder(s).";
        }

        long rateLimited = outcome.failedFolders().stream()
            .filter(failure -> failure.reason() != null && failure.reason().toLowerCase(Locale.ROOT).contains("ratelimit"))
            .count();
        if (rateLimited > 0) {
            return "Linked! " + (granted > 0 ? granted + " folder(s) granted now and " : "Your ") + rateLimited
                + " folder(s) queued while Google rate limits us; they'll arrive within the hour.";
        }

        long rejectedByGoogle = outcome.failedFolders().stream()
            .filter(failure -> failure.statusCode() >= 400 && failure.statusCode() < 500)
            .count();
        if (rejectedByGoogle > 0) {
            return "Google rejected that address for " + rejectedByGoogle + " folder(s). Make sure it's a Google account email.";
        }

        return "Linked! " + outcome.failedFolders().size() + " folder(s) couldn't be granted right now; retrying within the hour.";
    }

    private DriveLinkWorkflow workflow(DrivePermissionService service) {
        return new DriveLinkWorkflow(service, config.get().getGoogleDriveConfig());
    }
}
