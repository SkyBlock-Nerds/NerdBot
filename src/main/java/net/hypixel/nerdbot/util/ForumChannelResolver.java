package net.hypixel.nerdbot.util;

import com.freya02.botcommands.api.BContext;
import com.freya02.botcommands.api.parameters.ParameterResolver;
import com.freya02.botcommands.api.parameters.SlashParameterResolver;
import com.freya02.botcommands.internal.application.slash.SlashCommandInfo;
import com.freya02.botcommands.internal.parameters.channels.ChannelResolver;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.interactions.commands.CommandInteractionPayload;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class ForumChannelResolver extends ParameterResolver implements SlashParameterResolver, ChannelResolver {

    public ForumChannelResolver() {
        super(ForumChannel.class);
    }

    @Override
    public @NotNull OptionType getOptionType() {
        return OptionType.CHANNEL;
    }

    @Override
    public @Nullable Object resolve(@NotNull BContext context, @NotNull SlashCommandInfo info, @NotNull CommandInteractionPayload event, @NotNull OptionMapping optionMapping) {
        return optionMapping.getAsChannel();
    }

    @Override
    public @NotNull EnumSet<ChannelType> getChannelTypes() {
        return EnumSet.of(ChannelType.FORUM);
    }
}
