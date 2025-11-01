package net.hypixel.nerdbot.discord.storage.database.model.user.generator;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
public class GeneratorHistory {

    private static final int MAX_COMMANDS = 25;

    private final List<String> commandHistory = new ArrayList<>();

    public GeneratorHistory() {
    }

    /**
     * Add a command to the history. We will only store a set number of commands.
     * The maximum number of commands is defined by {@link #MAX_COMMANDS}.
     *
     * @param command The slash command string to add
     */
    public void addCommand(String command) {
        if (commandHistory.size() >= MAX_COMMANDS) {
            commandHistory.removeFirst();
        }

        commandHistory.remove(command);
        commandHistory.add(command);
    }
}
