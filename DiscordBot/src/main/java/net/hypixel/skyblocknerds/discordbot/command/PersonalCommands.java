package net.hypixel.skyblocknerds.discordbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.components.InteractionConstraints;
import com.freya02.botcommands.api.pagination.paginator.Paginator;
import com.freya02.botcommands.api.pagination.paginator.PaginatorBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.hypixel.skyblocknerds.api.cache.suggestion.Suggestion;
import net.hypixel.skyblocknerds.api.cache.suggestion.SuggestionCache;
import net.hypixel.skyblocknerds.database.repository.RepositoryManager;
import net.hypixel.skyblocknerds.database.repository.impl.DiscordUserRepository;
import net.hypixel.skyblocknerds.discordbot.DiscordBot;

import java.util.ArrayList;
import java.util.List;

public class PersonalCommands extends ApplicationCommand {

    private static final String COMMAND_NAME = "my";

    private final DiscordUserRepository discordUserRepository;

    public PersonalCommands() {
        this.discordUserRepository = RepositoryManager.getInstance().getRepository(DiscordUserRepository.class);
    }

    @JDASlashCommand(name = COMMAND_NAME, subcommand = "profile", description = "View your profile")
    public void viewPersonalProfile(GuildSlashEvent event) {
        event.deferReply(true).complete();

        discordUserRepository.findById(event.getMember().getId()).ifPresentOrElse(user -> {
            event.getHook().editOriginal("Your profile: " + user).queue();
        }, () -> {
            event.getHook().editOriginal("Couldn't find your profile!").queue();
        });
    }

    @JDASlashCommand(name = COMMAND_NAME, subcommand = "suggestions", description = "View statistics on your suggestions")
    public void viewPersonalSuggestions(GuildSlashEvent event) {
        SuggestionCache suggestionCache = DiscordBot.getSuggestionCache();
        suggestionCache.getSuggestionsByAuthor(event.getMember().getId()).ifPresentOrElse(suggestions -> {
            List<EmbedBuilder> embedBuilders = new ArrayList<>();

            for (Suggestion suggestion : suggestions) {
                embedBuilders.add(createSuggestionEmbed(suggestion));
            }

            Paginator paginator = new PaginatorBuilder()
                .setConstraints(InteractionConstraints.ofUsers(event.getUser()))
                .useDeleteButton(false)
                .setMaxPages(suggestions.size())
                .setPaginatorSupplier((instance, messageBuilder, components, page) -> embedBuilders.get(page).build())
                .build();

            event.reply(MessageCreateData.fromEditData(paginator.get()))
                .setEphemeral(true)
                .queue();
        }, () -> {
            event.reply("Couldn't find your suggestions!").setEphemeral(true).queue();
        });
    }

    private EmbedBuilder createSuggestionEmbed(Suggestion suggestion) {
        return new EmbedBuilder()
            .setTitle(suggestion.getThreadTitle())
            .setDescription(String.join(", ", suggestion.getPostTags()) + "\n" + suggestion.getMessageContent())
            .addField("Reactions", mapReactionNames(suggestion.getReactions()), false)
            .setTimestamp(suggestion.getCreatedAt());
    }

    private String mapReactionNames(List<Suggestion.Reaction> reactions) {
        StringBuilder builder = new StringBuilder();

        for (Suggestion.Reaction reaction : reactions) {
            builder.append(reaction.getCount())
                .append(" <:")
                .append(reaction.getEmojiName())
                .append(":")
                .append(reaction.getEmojiId())
                .append("> ");
        }

        return builder.toString().trim();
    }
}
