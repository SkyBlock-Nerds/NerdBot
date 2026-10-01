package net.hypixel.nerdbot.app.command;

import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashAutocompleteHandler;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.app.config.GeneratorConfig;
import net.hypixel.nerdbot.app.generation.pack.update.PackState;
import net.hypixel.nerdbot.app.generation.pack.update.PackUpdateNotifier;
import net.hypixel.nerdbot.app.generation.pack.update.PackUpdateOutcome;
import net.hypixel.nerdbot.app.generation.pack.update.PackUpdater;
import net.hypixel.nerdbot.discord.BotEnvironment;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Admin controls for resource pack auto-update. */
@Slf4j
public class PackCommands {

    private static final String NOT_CONFIGURED_MESSAGE = "Resource pack auto-update is not configured.";
    private static final String PACK_OPTION_DESCRIPTION = "The auto-updated pack (optional when only one is configured)";
    private static final int DISCORD_MESSAGE_LIMIT = 2000;

    @SlashCommand(name = "pack", subcommand = "status", description = "Show the auto-updated resource pack state", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void status(SlashCommandInteractionEvent event, @SlashOption(autocompleteId = "auto-update-packs", description = PACK_OPTION_DESCRIPTION, required = false) String pack) {
        Optional<PackUpdater> updater = SkyBlockNerdsBot.packUpdater();
        if (updater.isEmpty()) {
            event.reply(NOT_CONFIGURED_MESSAGE).setEphemeral(true).queue();
            return;
        }

        Optional<GeneratorConfig.PackDefinition> definition = resolveDefinition(event, pack);
        if (definition.isEmpty()) {
            return;
        }

        String packId = PackUpdater.packIdOf(definition.get());
        PackState.Slot slot = updater.get().store().slot(packId);
        StringBuilder message = new StringBuilder("**").append(packId).append("**\n");
        message.append("Live: ").append(slot.current() == null ? "configured pack" : PackUpdateNotifier.describe(slot.current())
            + ", applied <t:" + slot.current().appliedAtEpochMs() / 1000 + ":R>").append('\n');
        message.append("Previous: ").append(slot.previous() == null ? "none" : PackUpdateNotifier.describe(slot.previous())).append('\n');
        message.append("Rejected versions remembered: ").append(slot.rejected().size()).append('\n');
        message.append("Last check: ").append(updater.get().lastOutcome(packId)
            .map(recorded -> summarize(recorded.outcome()) + " <t:" + recorded.atEpochMs() / 1000 + ":R>")
            .orElse("none since the bot started"));

        event.reply(truncate(message.toString())).setEphemeral(true).queue();
    }

    @SlashCommand(name = "pack", subcommand = "check", description = "Check Hypixel for a newer resource pack now", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void check(SlashCommandInteractionEvent event, @SlashOption(autocompleteId = "auto-update-packs", description = PACK_OPTION_DESCRIPTION, required = false) String pack) {
        runAsync(event, pack, "check", PackUpdater::checkForUpdate);
    }

    @SlashCommand(name = "pack", subcommand = "rollback", description = "Go back to the previous resource pack and stop re-applying the current one", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void rollback(SlashCommandInteractionEvent event, @SlashOption(autocompleteId = "auto-update-packs", description = PACK_OPTION_DESCRIPTION, required = false) String pack) {
        runAsync(event, pack, "rollback", PackUpdater::rollback);
    }

    @SlashAutocompleteHandler(id = "auto-update-packs")
    public List<Command.Choice> autoUpdatePacks(CommandAutoCompleteInteractionEvent event) {
        String typed = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
        return PackUpdater.enabledDefinitions(packConfig()).stream()
            .map(PackUpdater::packIdOf)
            .filter(id -> id.contains(typed))
            .limit(25)
            .map(id -> new Command.Choice(id, id))
            .toList();
    }

    private interface PackAction {
        PackUpdateOutcome run(PackUpdater updater, GeneratorConfig.PackDefinition definition);
    }

    /** Download and load can take seconds, so the work runs off the interaction thread. */
    private void runAsync(SlashCommandInteractionEvent event, String pack, String actionName, PackAction action) {
        Optional<PackUpdater> updater = SkyBlockNerdsBot.packUpdater();
        if (updater.isEmpty()) {
            event.reply(NOT_CONFIGURED_MESSAGE).setEphemeral(true).queue();
            return;
        }

        Optional<GeneratorConfig.PackDefinition> definition = resolveDefinition(event, pack);
        if (definition.isEmpty()) {
            return;
        }

        log.info("Member {} invoked /pack {} for {}", event.getUser().getId(), actionName, definition.get().getId());
        event.deferReply(true).queue();
        BotEnvironment.EXECUTOR_SERVICE.execute(() -> {
            PackUpdateOutcome outcome = action.run(updater.get(), definition.get());
            event.getHook().editOriginal(truncate(summarize(outcome))).queue();
        });
    }

    /** Replies with an error and returns empty when the option does not pick exactly one auto-updated pack. */
    private Optional<GeneratorConfig.PackDefinition> resolveDefinition(SlashCommandInteractionEvent event, String pack) {
        List<GeneratorConfig.PackDefinition> definitions = PackUpdater.enabledDefinitions(packConfig());

        if (pack == null || pack.isBlank()) {
            if (definitions.size() == 1) {
                return Optional.of(definitions.getFirst());
            }
            event.reply(definitions.isEmpty() ? NOT_CONFIGURED_MESSAGE : "More than one pack is auto-updated, so choose one with the pack option.")
                .setEphemeral(true).queue();
            return Optional.empty();
        }

        String wanted = pack.trim().toLowerCase(Locale.ROOT);
        Optional<GeneratorConfig.PackDefinition> match = definitions.stream()
            .filter(definition -> PackUpdater.packIdOf(definition).equals(wanted))
            .findFirst();
        if (match.isEmpty()) {
            event.reply("`" + pack + "` is not an auto-updated pack.").setEphemeral(true).queue();
        }
        return match;
    }

    private static GeneratorConfig.ResourcePackConfig packConfig() {
        return SkyBlockNerdsBot.config().getGeneratorConfig().getResourcePacks();
    }

    /** Failure reasons can be long, and Discord rejects messages over its length limit. */
    private static String truncate(String text) {
        return text.length() <= DISCORD_MESSAGE_LIMIT ? text : text.substring(0, DISCORD_MESSAGE_LIMIT);
    }

    private static String summarize(PackUpdateOutcome outcome) {
        return switch (outcome) {
            case PackUpdateOutcome.UpToDate upToDate -> upToDate.previouslyRejected()
                ? "Hypixel's latest version (" + PackUpdateNotifier.shortHash(upToDate.sha1()) + ") was rejected before, so it was skipped."
                : "Already on Hypixel's latest version (" + PackUpdateNotifier.shortHash(upToDate.sha1()) + ").";
            case PackUpdateOutcome.Applied applied -> "Applied " + PackUpdateNotifier.describe(applied.to())
                + " (" + applied.previousItemCount() + " to " + applied.itemCount() + " items).";
            case PackUpdateOutcome.Rejected rejected -> "Rejected format " + rejected.packFormat() + " ("
                + PackUpdateNotifier.shortHash(rejected.sha1()) + "): " + rejected.reason();
            case PackUpdateOutcome.TransientFailure failure -> "Check failed, will retry on the next poll: " + failure.reason();
            case PackUpdateOutcome.RolledBack rolledBack -> "Rolled back to " + PackUpdateNotifier.describe(rolledBack.to())
                + ". " + PackUpdateNotifier.shortHash(rolledBack.from().sha1()) + " will not be re-applied.";
            case PackUpdateOutcome.Unavailable unavailable -> "Not possible: " + unavailable.reason();
            case PackUpdateOutcome.Busy busy -> "A pack check or rollback is already running, try again shortly.";
        };
    }
}
