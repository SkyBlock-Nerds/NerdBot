package net.hypixel.nerdbot.app.command;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Component and modal ids for the punishment panels, shaped {@code prefix-part-part-...}. Buttons on
 * messages sent before a deploy still carry the old ids, so these formats must not change.
 */
final class PunishmentComponentIds {

    static final String TYPE = "punish-type";
    static final String MODAL = "punish-modal";
    static final String DETAIL = "punish-detail";
    static final String NAV = "punish-nav";
    static final String DELETE = "punish-delete";
    static final String CONFIRM_YES = "punish-confirm-yes";
    static final String CONFIRM_NO = "punish-confirm-no";
    static final String BACK = "punish-back";

    private PunishmentComponentIds() {
    }

    static String build(String prefix, Object... parts) {
        return prefix + "-" + Arrays.stream(parts).map(String::valueOf).collect(Collectors.joining("-"));
    }

    /**
     * Splits an id made by {@link #build} back into {@code count} parts. The last part keeps any
     * dashes, since punishment ids are UUIDs. Empty when the id has a different prefix, too few
     * parts, or an empty part.
     */
    static Optional<List<String>> parse(String prefix, String id, int count) {
        if (id == null || !id.startsWith(prefix + "-")) {
            return Optional.empty();
        }

        String[] parts = id.substring(prefix.length() + 1).split("-", count);
        if (parts.length != count || Arrays.stream(parts).anyMatch(String::isEmpty)) {
            return Optional.empty();
        }

        return Optional.of(List.of(parts));
    }
}
