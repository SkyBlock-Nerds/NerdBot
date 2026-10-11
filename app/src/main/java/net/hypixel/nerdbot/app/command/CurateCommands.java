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
import net.hypixel.nerdbot.app.curator.Curator;
import net.hypixel.nerdbot.app.curator.ForumChannelCurator;
import net.hypixel.nerdbot.marmalade.format.TimeUtils;
import net.hypixel.nerdbot.marmalade.storage.database.model.greenlit.GreenlitMessage;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;

/** /curate: greenlights the suggestions in a forum that have enough votes. */
public class CurateCommands {

    private static final Duration PROGRESS_INTERVAL = Duration.ofSeconds(1);

    private final Function<Boolean, Curator<ForumChannel, ThreadChannel>> curators;
    private final Executor backgroundWork;

    @Inject
    public CurateCommands(Executor backgroundWork) {
        this(ForumChannelCurator::new, backgroundWork);
    }

    CurateCommands(Function<Boolean, Curator<ForumChannel, ThreadChannel>> curators, Executor backgroundWork) {
        this.curators = curators;
        this.backgroundWork = backgroundWork;
    }

    @SlashCommand(name = "curate", description = "Manually run the curation process", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void curate(SlashCommandInteractionEvent event, @SlashOption ForumChannel channel, @SlashOption(description = "Run the curator without greenlighting suggestions", required = false) Boolean readOnly) {
        run(event, channel, Boolean.TRUE.equals(readOnly));
    }

    void run(IReplyCallback event, ForumChannel channel, boolean readOnly) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        Curator<ForumChannel, ThreadChannel> curator = curators.apply(readOnly);

        backgroundWork.execute(() -> {
            ProgressUpdates progress = ProgressUpdates.every(PROGRESS_INTERVAL, "curate-progress", () -> {
                ThreadChannel current = curator.getCurrentObject();
                if (current != null) {
                    hook.editOriginal(progressMessage(curator.getIndex(), curator.getTotal(), System.currentTimeMillis() - curator.getStartTime(), current.getJumpUrl())).queue();
                }
            });

            List<GreenlitMessage> greenlit;
            try {
                greenlit = curator.curate(channel);
            } catch (RuntimeException exception) {
                progress.finish(() -> CommandReplies.fail(hook, "Curation failed. Please try again later.", exception));
                return;
            }

            progress.finish(() -> hook.editOriginal(resultMessage(greenlit.size(), curator.getEndTime() - curator.getStartTime())).queue());
        });
    }

    static String progressMessage(int index, int total, long elapsedMs, String currentUrl) {
        return "Curation progress: " + index + "/" + total + " in " + TimeUtils.formatMsCompact(elapsedMs) + "\nCurrently looking at " + currentUrl;
    }

    static String resultMessage(int greenlit, long durationMs) {
        return greenlit == 0 ? "No suggestions were greenlit" : String.format("Greenlit %d suggestions in %dms!", greenlit, durationMs);
    }
}
