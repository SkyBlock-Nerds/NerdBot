package net.hypixel.nerdbot.app.util;

import net.aerh.imagegenerator.data.Icon;
import net.aerh.imagegenerator.data.Stat;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the text rows for {@code /gen symbols}: every icon and stat placeholder with its base
 * character and any pack-specific override characters.
 * <p>
 * Pack glyphs live in the Unicode private use area, so Discord's own font renders them as empty
 * boxes; those are printed as {@code U+XXXX} codepoints instead. The generated images render them
 * fine because the glyphs are baked into the generator's fonts.
 */
public final class GlyphListing {

    private GlyphListing() {
    }

    /**
     * Builds one row per icon and stat placeholder, alphabetically by name.
     *
     * @param filter optional case-insensitive substring to match against placeholder names
     *
     * @return the matching rows, possibly empty
     */
    public static List<String> buildRows(@Nullable String filter) {
        List<String> rows = new ArrayList<>();

        for (Icon icon : Icon.getIcons()) {
            if (matches(icon.getName(), filter)) {
                rows.add(row(icon.getName(), icon.getIcon(), icon.getPackOverrides()));
            }
        }

        for (Stat stat : Stat.getStats()) {
            if (matches(stat.getName(), filter)) {
                rows.add(row(stat.getName(), stat.getIcon(), stat.getPackOverrides()));
            }
        }

        rows.sort(String.CASE_INSENSITIVE_ORDER);
        return rows;
    }

    /**
     * Joins a page of rows under a short usage header.
     *
     * @param rows the rows for the current page
     *
     * @return the page text
     */
    public static String buildPage(List<String> rows) {
        return "Use `%%name%%` in generator text. Pack glyphs are shown as codepoints because"
            + " Discord cannot display them; generated images can.\n"
            + String.join("\n", rows);
    }

    private static boolean matches(@Nullable String name, @Nullable String filter) {
        if (name == null) {
            return false;
        }
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return name.toLowerCase(Locale.ROOT).contains(filter.trim().toLowerCase(Locale.ROOT));
    }

    private static String row(String name, @Nullable String baseCharacter, @Nullable Map<String, String> packOverrides) {
        StringBuilder row = new StringBuilder("`%%").append(name).append("%%` ").append(printable(baseCharacter));

        if (packOverrides != null) {
            packOverrides.forEach((packId, character) ->
                row.append(" (").append(packId).append(": ").append(printable(character)).append(")"));
        }

        return row.toString();
    }

    private static String printable(@Nullable String character) {
        if (character == null || character.isEmpty()) {
            return "?";
        }

        int codePoint = character.codePointAt(0);
        boolean unrenderable = (codePoint >= 0xE000 && codePoint <= 0xF8FF) || codePoint == 0x12DE;
        return unrenderable ? "U+%04X".formatted(codePoint) : character;
    }
}
