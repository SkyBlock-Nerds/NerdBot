package net.hypixel.nerdbot.api.command.slash;

import net.dv8tion.jda.api.interactions.commands.OptionType;

public record CommandArgument(OptionType optionType, String argument, String description, boolean required) {

    public static CommandArgument of(OptionType optionType, String argument, String description, boolean required) {
        return new CommandArgument(optionType, argument, description, required);
    }

    public static CommandArgument of(OptionType optionType, String argument, String description) {
        return new CommandArgument(optionType, argument, description, false);
    }

}
