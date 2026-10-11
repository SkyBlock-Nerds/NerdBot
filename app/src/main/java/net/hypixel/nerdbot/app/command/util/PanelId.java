package net.hypixel.nerdbot.app.command.util;

import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The shape of a component or modal id, {@code prefix-part-part-...}, for panels only the user who
 * opened them may use. Buttons on messages posted before a deploy still carry the old ids, so a
 * shape must not change once it's in use.
 *
 * @param prefix      what every id of this shape starts with, also used in the handler annotation
 * @param parts       how many parts follow the prefix
 * @param ownerIndex  which part is the id of the user allowed to use the panel
 * @param dashedIndex which part may itself contain dashes, such as a UUID
 */
public record PanelId(String prefix, int parts, int ownerIndex, int dashedIndex) {

    public static final String OUTDATED = "This panel is out of date. Run the command again.";

    public PanelId {
        Objects.requireNonNull(prefix, "prefix");
        if (parts < 1 || ownerIndex < 0 || ownerIndex >= parts || dashedIndex < 0 || dashedIndex >= parts) {
            throw new IllegalArgumentException("Invalid panel id shape for " + prefix + ": parts=" + parts + ", ownerIndex=" + ownerIndex + ", dashedIndex=" + dashedIndex);
        }
    }

    /** A shape whose last part is the one that may contain dashes. */
    public static PanelId of(String prefix, int parts, int ownerIndex) {
        return new PanelId(prefix, parts, ownerIndex, parts - 1);
    }

    public String build(Object... values) {
        if (values.length != parts) {
            throw new IllegalArgumentException(prefix + " ids have " + parts + " parts, got " + values.length);
        }
        return prefix + "-" + Arrays.stream(values).map(String::valueOf).collect(Collectors.joining("-"));
    }

    /**
     * Splits an id made by {@link #build} back into its parts. Empty when the id has a different
     * prefix, too few parts, or an empty part.
     */
    public Optional<List<String>> parse(String id) {
        if (id == null || !id.startsWith(prefix + "-")) {
            return Optional.empty();
        }

        String[] pieces = id.substring(prefix.length() + 1).split("-", -1);
        int extra = pieces.length - parts;
        if (extra < 0 || Arrays.stream(pieces).anyMatch(String::isEmpty)) {
            return Optional.empty();
        }

        List<String> result = new ArrayList<>(parts);
        result.addAll(Arrays.asList(pieces).subList(0, dashedIndex));
        result.add(String.join("-", Arrays.copyOfRange(pieces, dashedIndex, dashedIndex + extra + 1)));
        result.addAll(Arrays.asList(pieces).subList(dashedIndex + extra + 1, pieces.length));
        return Optional.of(List.copyOf(result));
    }

    /**
     * The parts of {@code id}, or empty after telling the user why not: the id doesn't have this
     * shape (shown {@link #OUTDATED}), or they aren't the user who opened the panel (shown
     * {@code notYourPanel}).
     */
    public Optional<List<String>> parseFor(IReplyCallback interaction, String id, String notYourPanel) {
        Optional<List<String>> parsed = parse(id);
        if (parsed.isEmpty()) {
            interaction.reply(OUTDATED).setEphemeral(true).queue();
            return Optional.empty();
        }

        if (!interaction.getUser().getId().equals(parsed.get().get(ownerIndex))) {
            interaction.reply(notYourPanel).setEphemeral(true).queue();
            return Optional.empty();
        }

        return parsed;
    }
}
