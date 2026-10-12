package net.hypixel.nerdbot.app.command;

import jakarta.inject.Inject;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.hypixel.nerdbot.app.command.util.CommandReplies;
import net.hypixel.nerdbot.app.command.util.ProgressUpdates;
import net.hypixel.nerdbot.app.curator.Curation;
import net.hypixel.nerdbot.app.curator.ForumChannelCurator;
import net.hypixel.nerdbot.marmalade.format.TimeUtils;

import java.time.Duration;
import java.util.concurrent.Executor;

/** /curate: greenlights the suggestions in a forum that have enough votes. */
public class CurateCommands {

    static final String BUSY_MESSAGE = "A curation is already running. Try again once it's finished.";
    private static final Duration PROGRESS_INTERVAL = Duration.ofSeconds(1);

    /** {@link ForumChannelCurator#curate}, so tests can stand in for it. */
    @FunctionalInterface
    interface Curate {
        boolean curate(ForumChannel forum, Curation curation);
    }

    private final Curate curator;
    private final Executor backgroundWork;

    @Inject
    public CurateCommands(ForumChannelCurator curator, Executor backgroundWork) {
        this(curator::curate, backgroundWork);
    }

    CurateCommands(Curate curator, Executor backgroundWork) {
        this.curator = curator;
        this.backgroundWork = backgroundWork;
    }

    @SlashCommand(name = "curate", description = "Manually run the curation process", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void curate(SlashCommandInteractionEvent event, @SlashOption ForumChannel channel, @SlashOption(description = "Run the curator without greenlighting suggestions", required = false) Boolean readOnly) {
        run(event, channel, Boolean.TRUE.equals(readOnly));
    }

    void run(IReplyCallback event, ForumChannel channel, boolean dryRun) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        Curation curation = new Curation(dryRun);

        backgroundWork.execute(() -> {
            ProgressUpdates progress = ProgressUpdates.every(PROGRESS_INTERVAL, "curate-progress", () -> {
                ThreadChannel current = curation.current();
                if (current != null) {
                    hook.editOriginal(progressMessage(curation.index(), curation.total(), System.currentTimeMillis() - curation.startedAt(), current.getJumpUrl())).queue();
                }
            });

            boolean ran;
            try {
                ran = curator.curate(channel, curation);
            } catch (RuntimeException exception) {
                progress.finish(() -> CommandReplies.fail(hook, "Curation failed. Please try again later.", exception));
                return;
            }

            progress.finish(() -> hook.editOriginal(ran ? resultMessage(curation.greenlit().size(), curation.durationMs()) : BUSY_MESSAGE).queue());
        });
    }

    static String progressMessage(int index, int total, long elapsedMs, String currentUrl) {
        return "Curation progress: " + index + "/" + total + " in " + TimeUtils.formatMsCompact(elapsedMs) + "\nCurrently looking at " + currentUrl;
    }

    static String resultMessage(int greenlit, long durationMs) {
        return greenlit == 0 ? "No suggestions were greenlit" : String.format("Greenlit %d suggestions in %dms!", greenlit, durationMs);
    }
}
