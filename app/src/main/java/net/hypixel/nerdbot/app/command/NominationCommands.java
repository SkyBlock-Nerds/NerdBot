package net.hypixel.nerdbot.app.command;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.hypixel.nerdbot.app.command.util.CommandReplies;
import net.hypixel.nerdbot.app.nomination.InactivitySweepReport;
import net.hypixel.nerdbot.app.nomination.NominationSweepReport;
import net.hypixel.nerdbot.app.nomination.NominationSweeps;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.marmalade.discord.EmbedFactory;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.NominationInfo;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Forcing the nomination and inactivity sweeps, and clearing inactivity warnings. */
@Slf4j
public class NominationCommands {

    private static final String SWEEP_FAILED_MESSAGE = "The sweep failed. Check the logs for details.";

    private final NominationSweeps sweeps;
    private final DiscordUserStore users;
    private final Executor backgroundWork;

    @Inject
    public NominationCommands(NominationSweeps sweeps, DiscordUserStore users, Executor backgroundWork) {
        this.sweeps = sweeps;
        this.users = users;
        this.backgroundWork = backgroundWork;
    }

    @SlashCommand(name = "force", subcommand = "nominations", description = "Forcefully run the nomination process", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void forceNominations(SlashCommandInteractionEvent event, @SlashOption(description = "Only check users with the New Member role", required = false) Boolean newMembersOnly) {
        nominate(event, Boolean.TRUE.equals(newMembersOnly));
    }

    CompletableFuture<Void> nominate(IReplyCallback event, boolean newMembersOnly) {
        return runSweep(event, "run the nomination sweep", newMembersOnly
            ? () -> nominationReport("Nomination Sweep Complete (New Member)", sweeps.nominateNewMembers())
            : () -> nominationReport("Nomination Sweep Complete (Member)", sweeps.nominateMembers()));
    }

    @SlashCommand(name = "force", subcommand = "inactivity-check", description = "Forcefully run the inactivity check", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void forceInactiveCheck(SlashCommandInteractionEvent event, @SlashOption(description = "Only check users with the New Member role", required = false) Boolean newMembersOnly) {
        checkInactivity(event, Boolean.TRUE.equals(newMembersOnly));
    }

    CompletableFuture<Void> checkInactivity(IReplyCallback event, boolean newMembersOnly) {
        return runSweep(event, "run the inactivity sweep", newMembersOnly
            ? () -> inactivityReport("Inactivity Sweep Complete (New Member)", sweeps.checkNewMemberInactivity())
            : () -> inactivityReport("Inactivity Sweep Complete (Member)", sweeps.checkMemberInactivity()));
    }

    @SlashCommand(name = "force", subcommand = "restricted-inactivity-check", description = "Forcefully run the restricted inactivity check", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void forceRestrictedInactiveCheck(SlashCommandInteractionEvent event) {
        checkRestrictedInactivity(event);
    }

    CompletableFuture<Void> checkRestrictedInactivity(IReplyCallback event) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        return CommandReplies.finish(CompletableFuture.runAsync(sweeps::checkRestrictedChannelInactivity, backgroundWork), hook,
            "run the restricted inactivity sweep", SWEEP_FAILED_MESSAGE, done -> hook.editOriginal("Forced restricted inactivity check!").queue());
    }

    @SlashCommand(name = "reset-inactivity-check-data", description = "Reset the inactivity check data", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void resetInactiveCheckData(SlashCommandInteractionEvent event) {
        resetInactivityWarnings(event);
    }

    CompletableFuture<Void> resetInactivityWarnings(IReplyCallback event) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        return CommandReplies.finish(CompletableFuture.supplyAsync(this::resetEveryone, backgroundWork).thenCompose(saves -> saves), hook,
            "reset inactivity warnings", "Resetting the inactivity data failed. Check the logs for details.",
            reset -> hook.editOriginal(String.format("Reset inactivity check data for %d users.", reset)).queue());
    }

    /** Every stored member, not just those cached. The cached copy is used when there is one so nothing unsaved is lost. */
    private CompletableFuture<Integer> resetEveryone() {
        List<CompletableFuture<Void>> saves = users.getAllFromDatabase().stream()
            .map(stored -> users.findById(stored.getDiscordId()).orElse(stored))
            .map(user -> {
                clearWarnings(user);
                return users.save(user);
            })
            .toList();

        return CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new)).thenApply(done -> saves.size());
    }

    private static void clearWarnings(DiscordUser user) {
        if (user.getLastActivity() == null) {
            return;
        }

        NominationInfo nominationInfo = user.getLastActivity().getNominationInfo();
        nominationInfo.setTotalInactivityWarnings(0);
        nominationInfo.setLastInactivityWarningTimestamp(null);
    }

    private CompletableFuture<Void> runSweep(IReplyCallback event, String action, Supplier<MessageEmbed> sweep) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        return CommandReplies.finish(CompletableFuture.supplyAsync(sweep, backgroundWork), hook, action, SWEEP_FAILED_MESSAGE,
            report -> hook.editOriginalEmbeds(report).queue());
    }

    static MessageEmbed nominationReport(String title, NominationSweepReport report) {
        return EmbedFactory.success(title, null)
            .addField("Scanned", String.valueOf(report.scanned()), true)
            .addField("Eligible", String.valueOf(report.eligible()), true)
            .addField("Nominated", String.valueOf(report.nominated()), true)
            .addField("Below Threshold", String.valueOf(report.belowThreshold()), true)
            .addField("Already This Month", String.valueOf(report.alreadyThisMonth()), true)
            .addField("Ineligible", String.valueOf(report.ineligible()), true)
            .addField("Missing Member", String.valueOf(report.missingMember()), true)
            .addField("Duration", report.durationMs() + "ms", true)
            .build();
    }

    static MessageEmbed inactivityReport(String title, InactivitySweepReport report) {
        return new EmbedBuilder()
            .setTitle(title)
            .setColor(Color.ORANGE)
            .addField("Scanned", String.valueOf(report.scanned()), true)
            .addField("Warned This Month", String.valueOf(report.warnedThisMonth()), true)
            .addField("Skipped Already This Month", String.valueOf(report.skippedAlreadyThisMonth()), true)
            .addField("Ineligible", String.valueOf(report.ineligible()), true)
            .addField("Missing Member", String.valueOf(report.missingMember()), true)
            .addField("Duration", report.durationMs() + "ms", true)
            .build();
    }
}
