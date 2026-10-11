package net.hypixel.nerdbot.app.command.util;

import jakarta.inject.Inject;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.hypixel.nerdbot.app.suggestion.Suggestion;
import net.hypixel.nerdbot.app.suggestion.SuggestionCache;
import net.hypixel.nerdbot.discord.util.pagination.PaginatedResponse;
import net.hypixel.nerdbot.discord.util.pagination.PaginationManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The paged suggestion list behind /suggestions and /profile suggestions. */
public class SuggestionListings {

    /** Whose suggestions to list, and how. */
    public record Request(@Nullable Long ownerId, @Nullable String ownerName, @Nullable String ownerAvatar,
                          boolean showNames, boolean showRatio) {
    }

    private final SuggestionCache suggestions;
    private final JDA jda;

    @Inject
    public SuggestionListings(SuggestionCache suggestions, JDA jda) {
        this.suggestions = suggestions;
        this.jda = jda;
    }

    /** @param paginationId the button prefix, so the command that listed them handles the page buttons */
    public void show(IReplyCallback interaction, Request request, @Nullable String tags, @Nullable String title, @Nullable String type,
                     String paginationId) {
        InteractionHook hook = CommandReplies.deferPrivately(interaction);
        Member viewer = interaction.getMember();
        if (viewer == null) {
            hook.editOriginal("This command can only be used in a server!").queue();
            return;
        }

        Suggestion.ChannelType channelType = type == null || type.isBlank() ? Suggestion.ChannelType.NORMAL : Suggestion.ChannelType.getType(type);
        if (channelType == Suggestion.ChannelType.UNKNOWN) {
            hook.editOriginal("Invalid channel type provided!").queue();
            return;
        }

        List<Suggestion> matching = SuggestionCommandUtils.filterSuggestions(jda, suggestions.all(), viewer, request.ownerId(), tags, title, channelType);
        if (matching.isEmpty()) {
            hook.editOriginal(suggestions.isInitialized()
                ? "No suggestions found matching that filter!"
                : "Suggestions are still loading, please try again in a minute.").queue();
            return;
        }

        SuggestionCommandUtils.Listing listing = new SuggestionCommandUtils.Listing(viewer, tags, title, channelType,
            request.showNames(), request.showRatio(), request.ownerName(), request.ownerAvatar(), !suggestions.isInitialized(), jda);
        PaginatedResponse<Suggestion> pagination = SuggestionCommandUtils.createSuggestionsPagination(
            matching, SuggestionCommandUtils.buildSuggestionStats(jda, matching, viewer), listing, paginationId);

        pagination.sendMessage(interaction);
        hook.retrieveOriginal().queue(message -> PaginationManager.registerPagination(message.getId(), pagination));
    }
}
