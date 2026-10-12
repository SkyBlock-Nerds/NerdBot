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
import net.hypixel.nerdbot.app.nomination.NominationSweeps;
import net.hypixel.nerdbot.app.nomination.SweepReport;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.marmalade.discord.EmbedFactory;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.NominationInfo;

import java.awt.Color;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/** Forcing the nomination and inactivity sweeps, and clearing inactivity warnings. */
@Slf4j
public class NominationCommands {

    static final String SWEEP_FAILED_MESSAGE = "The sweep failed. Check the logs for details.";
    static final String SWEEP_BUSY_MESSAGE = "Another sweep is still running. Try again once it's finished.";
    // Discord allows an embed 25 fields
    private static final int MAX_GROUP_FIELDS = 25;

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
        return runSweep(event, "run the nomination sweep", newMembersOnly ? sweeps::nominateNewMembers : sweeps::nominateMembers,
            report -> nominationReport("Nomination Sweep Complete (" + (newMembersOnly ? "New Member" : "Member") + ")", report));
    }

    @SlashCommand(name = "force", subcommand = "inactivity-check", description = "Forcefully run the inactivity check", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void forceInactiveCheck(SlashCommandInteractionEvent event, @SlashOption(description = "Only check users with the New Member role", required = false) Boolean newMembersOnly) {
        checkInactivity(event, Boolean.TRUE.equals(newMembersOnly));
    }

    CompletableFuture<Void> checkInactivity(IReplyCallback event, boolean newMembersOnly) {
        return runSweep(event, "run the inactivity sweep", newMembersOnly ? sweeps::checkNewMemberInactivity : sweeps::checkMemberInactivity,
            report -> inactivityReport("Inactivity Sweep Complete (" + (newMembersOnly ? "New Member" : "Member") + ")", report));
    }

    @SlashCommand(name = "force", subcommand = "restricted-inactivity-check", description = "Forcefully run the restricted inactivity check", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void forceRestrictedInactiveCheck(SlashCommandInteractionEvent event) {
        checkRestrictedInactivity(event);
    }

    CompletableFuture<Void> checkRestrictedInactivity(IReplyCallback event) {
        return runSweep(event, "run the restricted inactivity sweep", sweeps::checkRestrictedChannelInactivity, NominationCommands::restrictedReport);
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

    private <T> CompletableFuture<Void> runSweep(IReplyCallback event, String action, Supplier<Optional<T>> sweep, Function<T, MessageEmbed> report) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        return CommandReplies.finish(CompletableFuture.supplyAsync(sweep, backgroundWork), hook, action, SWEEP_FAILED_MESSAGE,
            result -> result.ifPresentOrElse(
                done -> hook.editOriginalEmbeds(report.apply(done)).queue(),
                () -> hook.editOriginal(SWEEP_BUSY_MESSAGE).queue()));
    }

    static MessageEmbed nominationReport(String title, SweepReport report) {
        return EmbedFactory.success(title, null)
            .addField("Scanned", String.valueOf(report.scanned()), true)
            .addField("Eligible", String.valueOf(report.scanned() - report.ineligible() - report.missingMember()), true)
            .addField("Nominated", String.valueOf(report.acted()), true)
            .addField("Below Threshold", String.valueOf(report.notDue()), true)
            .addField("Already This Month", String.valueOf(report.alreadyThisMonth()), true)
            .addField("Ineligible", String.valueOf(report.ineligible()), true)
            .addField("Missing Member", String.valueOf(report.missingMember()), true)
            .addField("Failed", String.valueOf(report.failed()), true)
            .addField("Duration", report.durationMs() + "ms", true)
            .build();
    }

    static MessageEmbed inactivityReport(String title, SweepReport report) {
        return new EmbedBuilder()
            .setTitle(title)
            .setColor(Color.ORANGE)
            .addField("Scanned", String.valueOf(report.scanned()), true)
            .addField("Warned", String.valueOf(report.acted()), true)
            .addField("Active", String.valueOf(report.notDue()), true)
            .addField("Already Warned This Month", String.valueOf(report.alreadyThisMonth()), true)
            .addField("Ineligible", String.valueOf(report.ineligible()), true)
            .addField("Missing Member", String.valueOf(report.missingMember()), true)
            .addField("Failed", String.valueOf(report.failed()), true)
            .addField("Duration", report.durationMs() + "ms", true)
            .build();
    }

    static MessageEmbed restrictedReport(List<SweepReport> reports) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Restricted Inactivity Sweep Complete")
            .setColor(Color.ORANGE);
        if (reports.isEmpty()) {
            return embed.setDescription("No restricted channel groups have inactivity warnings turned on.").build();
        }

        reports.stream().limit(MAX_GROUP_FIELDS).forEach(report -> embed.addField(report.label(), String.format(
            "Warned: %d\nActive: %d\nAlready warned this month: %d\nFailed: %d",
            report.acted(), report.notDue(), report.alreadyThisMonth(), report.failed()), true));
        return embed.build();
    }
}
