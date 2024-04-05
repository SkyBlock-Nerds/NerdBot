package net.hypixel.nerdbot.generator.text.segment;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.util.ChatFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@Getter
@Setter
@ToString
public class ColorSegment {

    protected @NotNull String text;
    protected ChatFormat color = ChatFormat.WHITE;
    protected boolean italic, bold, underlined, obfuscated, strikethrough;

    public ColorSegment(@NotNull String text) {
        this.setText(text);
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static @NotNull LineSegment fromLegacy(@NotNull String legacyText) {
        return fromLegacy(legacyText, '&');
    }

    /**
     * This function takes in a legacy text string and converts it into a {@link ColorSegment}.
     * <p>
     * Legacy text strings use the {@link ChatFormat#SECTION_SYMBOL}. Many keyboards do not have this symbol however,
     * which is probably why it was chosen. To get around this, it is common practice to substitute
     * the symbol for another, then translate it later. Often '&' is used, but this can differ from person
     * to person. In case the string does not have a {@link ChatFormat#SECTION_SYMBOL}, the method also checks for the
     * {@param characterSubstitute}
     *
     * @param legacyText       The text to make into an object
     * @param symbolSubstitute The character substitute
     *
     * @return A TextObject representing the legacy text.
     */
    public static @NotNull LineSegment fromLegacy(@NotNull String legacyText, char symbolSubstitute) {
        return fromLegacyHandler(legacyText, symbolSubstitute, () -> new ColorSegment(""));
    }

    protected static @NotNull LineSegment fromLegacyHandler(@NotNull String legacyText, char symbolSubstitute, @NotNull Supplier<? extends ColorSegment> segmentSupplier) {
        LineSegment.Builder builder = LineSegment.builder();
        ColorSegment currentObject = segmentSupplier.get();
        StringBuilder text = new StringBuilder();

        for (int i = 0; i < legacyText.length(); i++) {
            char charAtIndex = legacyText.charAt(i);

            if (charAtIndex == ChatFormat.SECTION_SYMBOL || charAtIndex == symbolSubstitute) {
                if ((i + 1) > legacyText.length() - 1)
                    continue; // do nothing.

                // peek at the next character.
                char peek = legacyText.charAt(i + 1);

                if (ChatFormat.isValid(peek)) {
                    i += 1; // if valid
                    if (!text.isEmpty()) {
                        currentObject.setText(text.toString()); // create a new text object
                        builder.withSegments(currentObject); // append the current object.
                        currentObject = segmentSupplier.get(); // reset the current object.
                        text.setLength(0); // reset the buffer
                    }

                    ChatFormat color = Objects.requireNonNull(ChatFormat.of(peek));

                    switch (color) {
                        case OBFUSCATED:
                            currentObject.setObfuscated(true);
                            break;
                        case BOLD:
                            currentObject.setBold(true);
                            break;
                        case STRIKETHROUGH:
                            currentObject.setStrikethrough(true);
                            break;
                        case ITALIC:
                            currentObject.setItalic(true);
                            break;
                        case UNDERLINE:
                            currentObject.setUnderlined(true);
                            break;
                        case RESET:
                            // Reset everything.
                            currentObject.setColor(ChatFormat.WHITE);
                            currentObject.setObfuscated(false);
                            currentObject.setBold(false);
                            currentObject.setItalic(false);
                            currentObject.setUnderlined(false);
                            currentObject.setStrikethrough(false);
                            break;
                        default:
                            // emulate Minecraft's behavior of dropping styles that do not yet have an object.
                            currentObject = segmentSupplier.get();
                            currentObject.setColor(color);
                            break;
                    }
                } else {
                    text.append(charAtIndex);
                }
            } else {
                text.append(charAtIndex);
            }
        }

        // whatever we were working on when the loop exited
        currentObject.setText(text.toString());
        builder.withSegments(currentObject);

        return builder.build();
    }

    public Optional<ChatFormat> getColor() {
        return Optional.ofNullable(this.color);
    }

    public void setColor(@NotNull ChatFormat color) {
        this.color = color;
    }

    public void setText(@NotNull String value) {
        // Handle Unescaped Windows Apostrophe
        this.text = value
            .replaceAll("(?<!\\\\)'", "’") // Handle Unescaped Windows Apostrophe
            .replaceAll("\\\\'", "'"); // Remove Escaped Backslash
    }

    public @NotNull JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("text", this.getText());
        this.getColor().ifPresent(color -> object.addProperty("color", color.toJsonString()));

        if (this.isItalic()) object.addProperty("italic", true);
        if (this.isBold()) object.addProperty("bold", true);
        if (this.isUnderlined()) object.addProperty("underlined", true);
        if (this.isObfuscated()) object.addProperty("obfuscated", true);
        if (this.isStrikethrough()) object.addProperty("strikethrough", true);

        return object;
    }

    public @NotNull String toLegacy() {
        return this.toLegacy(ChatFormat.SECTION_SYMBOL);
    }

    /**
     * Takes an {@link ColorSegment} and transforms it into a legacy string.
     *
     * @param substitute The substitute character to use if you do not want to use {@link ChatFormat#SECTION_SYMBOL}
     *
     * @return A legacy string representation of a text object
     */
    public @NotNull String toLegacy(char substitute) {
        return this.toLegacyBuilder(substitute).toString();
    }

    protected @NotNull StringBuilder toLegacyBuilder() {
        return this.toLegacyBuilder(ChatFormat.SECTION_SYMBOL);
    }

    protected @NotNull StringBuilder toLegacyBuilder(char symbol) {
        StringBuilder builder = new StringBuilder();
        this.getColor().ifPresent(color -> builder.append(symbol).append(color.getCode()));
        if (this.isObfuscated()) builder.append(symbol).append(ChatFormat.OBFUSCATED.getCode());
        if (this.isBold()) builder.append(symbol).append(ChatFormat.BOLD.getCode());
        if (this.isStrikethrough()) builder.append(symbol).append(ChatFormat.STRIKETHROUGH.getCode());
        if (this.isUnderlined()) builder.append(symbol).append(ChatFormat.UNDERLINE.getCode());
        if (this.isItalic()) builder.append(symbol).append(ChatFormat.ITALIC.getCode());

        this.getColor().ifPresent(color -> {
            builder.setLength(0);
            builder.append(symbol).append(ChatFormat.RESET.getCode());
        });

        if (!this.getText().isEmpty()) {
            builder.append(this.getText());
        }

        return builder;
    }

    public @Nullable TextSegment toTextObject() {
        return TextSegment.fromJson(this.toJson());
    }

    public static class Builder implements ClassBuilder<ColorSegment> {
        protected String text = "";
        protected ChatFormat color = ChatFormat.WHITE;
        protected boolean italic, bold, underlined, obfuscated, strikethrough;

        public Builder isBold() {
            return this.isBold(true);
        }

        public Builder isBold(boolean value) {
            this.bold = value;
            return this;
        }

        public Builder isItalic() {
            return this.isItalic(true);
        }

        public Builder isItalic(boolean value) {
            this.italic = value;
            return this;
        }

        public Builder isObfuscated() {
            return this.isObfuscated(true);
        }

        public Builder isObfuscated(boolean value) {
            this.obfuscated = value;
            return this;
        }

        public Builder isStrikethrough() {
            return this.isStrikethrough(true);
        }

        public Builder isStrikethrough(boolean value) {
            this.strikethrough = value;
            return this;
        }

        public Builder isUnderlined() {
            return this.isUnderlined(true);
        }

        public Builder isUnderlined(boolean value) {
            this.underlined = value;
            return this;
        }

        public Builder withColor(@NotNull ChatFormat color) {
            this.color = color;
            return this;
        }

        public Builder withText(@NotNull String text) {
            this.text = text;
            return this;
        }

        @Override
        public @NotNull ColorSegment build() {
            ColorSegment colorSegment = new ColorSegment(this.text);
            colorSegment.setColor(this.color);
            colorSegment.setObfuscated(this.obfuscated);
            colorSegment.setItalic(this.italic);
            colorSegment.setBold(this.bold);
            colorSegment.setUnderlined(this.underlined);
            colorSegment.setStrikethrough(this.strikethrough);
            return colorSegment;
        }
    }
}
