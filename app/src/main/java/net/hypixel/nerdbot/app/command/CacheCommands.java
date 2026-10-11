package net.hypixel.nerdbot.app.command;

import jakarta.inject.Inject;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.hypixel.nerdbot.app.command.util.CommandReplies;
import net.hypixel.nerdbot.marmalade.exception.RepositoryException;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import net.hypixel.nerdbot.marmalade.storage.repository.Repository;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/** /cache: saving, loading and inspecting a repository's in-memory cache. */
public class CacheCommands {

    static final String UNKNOWN_REPOSITORY_MESSAGE = "Could not find a repository with that name!";

    private final Database database;
    private final Executor backgroundWork;

    @Inject
    public CacheCommands(Database database, Executor backgroundWork) {
        this.database = database;
        this.backgroundWork = backgroundWork;
    }

    @SlashCommand(name = "cache", subcommand = "force-save", description = "Force save the specified cache to the database", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void forceSaveRepository(SlashCommandInteractionEvent event, @SlashOption String repositoryName) {
        withRepository(event, repositoryName, "save the " + repositoryName + " cache", "An error occurred while saving the repository. Please try again later.",
            repository -> {
                repository.saveAllToDatabase();
                return String.format("Saved %d documents to the database!", repository.getCache().estimatedSize());
            });
    }

    @SlashCommand(name = "cache", subcommand = "force-load", description = "Forcefully load documents from the database into the cache", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void forceLoadDocuments(SlashCommandInteractionEvent event, @SlashOption String repositoryName) {
        withRepository(event, repositoryName, "load the " + repositoryName + " cache", "An error occurred while loading the repository. Please try again later.",
            repository -> {
                repository.loadAllDocumentsIntoCache();
                return String.format("Loaded %d documents from the database!", repository.getCache().estimatedSize());
            });
    }

    @SlashCommand(name = "cache", subcommand = "stats", description = "View cache statistics", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void cacheStats(SlashCommandInteractionEvent event, @SlashOption String repositoryName) {
        withRepository(event, repositoryName, "read the " + repositoryName + " cache stats", "An error occurred while getting the repository statistics. Please try again later.",
            repository -> repository.getCache().stats().toString());
    }

    /** Runs {@code work} off the dispatch thread against the named repository and replies with what it returns. */
    CompletableFuture<Void> withRepository(IReplyCallback event, String name, String action, String failureMessage,
                                           Function<Repository<?>, String> work) {
        InteractionHook hook = CommandReplies.deferPrivately(event);

        Repository<?> repository;
        try {
            repository = database.getRepositoryManager().getRepository(name);
        } catch (RepositoryException exception) {
            hook.editOriginal(UNKNOWN_REPOSITORY_MESSAGE).queue();
            return CompletableFuture.completedFuture(null);
        }

        if (repository == null) {
            hook.editOriginal(UNKNOWN_REPOSITORY_MESSAGE).queue();
            return CompletableFuture.completedFuture(null);
        }

        return CommandReplies.finish(CompletableFuture.supplyAsync(() -> work.apply(repository), backgroundWork), hook, action, failureMessage,
            message -> hook.editOriginal(message).queue());
    }
}
