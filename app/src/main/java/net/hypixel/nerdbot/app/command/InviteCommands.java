package net.hypixel.nerdbot.app.command;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.hypixel.nerdbot.app.channel.LogChannel;
import net.hypixel.nerdbot.app.command.util.CommandReplies;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/** /invites: single-use invites for new members, and clearing them out. */
@Slf4j
public class InviteCommands {

    static final int MAX_INVITES = 25;
    static final String DEFAULT_CHANNEL_NAME = "limbo";

    private final LogChannel logChannel;

    @Inject
    public InviteCommands(LogChannel logChannel) {
        this.logChannel = logChannel;
    }

    @SlashCommand(name = "invites", subcommand = "create", description = "Generate a bunch of invites for a specific channel.", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void createInvites(SlashCommandInteractionEvent event, @SlashOption int amount, @SlashOption(required = false) TextChannel channel) {
        create(event, amount, channel);
    }

    CompletableFuture<Void> create(IReplyCallback event, int amount, @Nullable TextChannel channel) {
        if (amount < 1 || amount > MAX_INVITES) {
            CommandReplies.replyPrivately(event, "You can create between 1 and " + MAX_INVITES + " invites at a time.");
            return CompletableFuture.completedFuture(null);
        }

        TextChannel target = channel != null ? channel : defaultChannel(event.getGuild());
        if (target == null) {
            CommandReplies.replyPrivately(event, "There's no #" + DEFAULT_CHANNEL_NAME + " channel to default to, so please pick a channel.");
            return CompletableFuture.completedFuture(null);
        }

        InteractionHook hook = CommandReplies.deferPrivately(event);
        List<CompletableFuture<Invite>> requests;
        try {
            requests = IntStream.range(0, amount)
                .mapToObj(index -> target.createInvite().setUnique(true).setMaxAge(7L, TimeUnit.DAYS).setMaxUses(1).submit())
                .toList();
        } catch (InsufficientPermissionException exception) {
            hook.editOriginal("I do not have the correct permission to create invites in " + target.getAsMention() + "!").queue();
            return CompletableFuture.completedFuture(null);
        }

        return settle(requests).thenAccept(invites -> {
            User user = event.getUser();
            invites.forEach(invite -> log.info("Created temporary invite '{}' for channel {} (ID: {}) by {} (ID: {})",
                invite.getUrl(), target.getName(), target.getId(), user.getName(), user.getId()));

            if (!invites.isEmpty()) {
                logChannel.send(new EmbedBuilder()
                    .setTitle("Invites Created")
                    .setDescription(user.getAsMention() + " created " + invites.size() + " invite(s) for " + target.getAsMention() + ".")
                    .build());
            }

            hook.editOriginal(createdMessage(invites.stream().map(Invite::getUrl).toList(), amount - invites.size())).queue();
        });
    }

    static String createdMessage(List<String> urls, int failed) {
        StringBuilder message = new StringBuilder("**Created ").append(urls.size()).append(" invites:**\n");
        urls.forEach(url -> message.append(url).append('\n'));
        if (failed > 0) {
            message.append("Couldn't create ").append(failed).append(" more; check my permissions and the logs.");
        }
        return message.toString();
    }

    private static @Nullable TextChannel defaultChannel(@Nullable Guild guild) {
        return guild == null ? null : guild.getTextChannelsByName(DEFAULT_CHANNEL_NAME, true).stream().findFirst().orElse(null);
    }

    @SlashCommand(name = "invites", subcommand = "delete", description = "Delete all active invites.", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void deleteInvites(SlashCommandInteractionEvent event) {
        delete(event);
    }

    CompletableFuture<Void> delete(IReplyCallback event) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        User user = event.getUser();

        CompletableFuture<Void> work = event.getGuild().retrieveInvites().submit()
            .thenCompose(invites -> settle(invites.stream()
                .map(invite -> invite.delete().submit().thenApply(deleted -> {
                    log.info("{} (ID: {}) deleted invite {}", user.getName(), user.getId(), invite.getUrl());
                    return invite;
                }))
                .toList())
                .thenAccept(deleted -> {
                    logChannel.send(new EmbedBuilder()
                        .setTitle("Invites Deleted")
                        .setDescription(user.getAsMention() + " deleted " + deleted.size() + " invite(s).")
                        .build());

                    int failed = invites.size() - deleted.size();
                    hook.editOriginal(String.format("Deleted %d invites!", deleted.size())
                        + (failed > 0 ? " Couldn't delete " + failed + "; check the logs." : "")).queue();
                }));

        return work.exceptionally(error -> {
            CommandReplies.fail(hook, "Couldn't load the server's invites. Please try again later.", error);
            return null;
        });
    }

    /** Waits for every request and keeps the ones that worked; failures are logged. */
    private static <T> CompletableFuture<List<T>> settle(List<CompletableFuture<T>> requests) {
        List<CompletableFuture<T>> handled = requests.stream()
            .map(request -> request.exceptionally(error -> {
                log.warn("An invite request failed", error);
                return null;
            }))
            .toList();

        return CompletableFuture.allOf(handled.toArray(CompletableFuture[]::new))
            .thenApply(done -> handled.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList());
    }
}
