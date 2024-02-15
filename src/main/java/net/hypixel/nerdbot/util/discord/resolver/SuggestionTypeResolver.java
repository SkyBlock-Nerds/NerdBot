package net.hypixel.nerdbot.util.discord.resolver;

import com.freya02.botcommands.api.BContext;
import com.freya02.botcommands.api.parameters.ParameterResolver;
import com.freya02.botcommands.api.parameters.SlashParameterResolver;
import com.freya02.botcommands.internal.application.slash.SlashCommandInfo;
import net.dv8tion.jda.api.interactions.commands.CommandInteractionPayload;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.hypixel.nerdbot.cache.suggestion.Suggestion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuggestionTypeResolver extends ParameterResolver implements SlashParameterResolver {

    public SuggestionTypeResolver() {
        super(Suggestion.ChannelType.class);
    }

    @Override
    public @Nullable Object resolve(@NotNull BContext context, @NotNull SlashCommandInfo info, @NotNull CommandInteractionPayload event, @NotNull OptionMapping optionMapping) {
        return Suggestion.ChannelType.getType(optionMapping.getAsString());
    }

    @Override
    public @NotNull OptionType getOptionType() {
        return OptionType.STRING;
    }
}
