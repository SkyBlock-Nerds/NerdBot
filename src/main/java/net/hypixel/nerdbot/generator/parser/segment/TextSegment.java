package net.hypixel.nerdbot.generator.parser.segment;


import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.generator.ClassBuilder;
import net.hypixel.nerdbot.util.ChatFormat;
import net.hypixel.nerdbot.generator.parser.event.ClickEvent;
import net.hypixel.nerdbot.generator.parser.event.HoverEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@Getter
@Setter
@ToString
public final class TextSegment extends ColorSegment {

    private @NotNull Optional<ClickEvent> clickEvent = Optional.empty();
    private @NotNull Optional<HoverEvent> hoverEvent = Optional.empty();

    public TextSegment(@NotNull String text) {
        super(text);
    }

    @Override
    public @NotNull JsonObject toJson() {
        JsonObject object = super.toJson(); // ColorSegment#toJson
        this.getClickEvent().ifPresent(clickEvent -> object.add("clickEvent", clickEvent.toJson()));
        this.getHoverEvent().ifPresent(hoverEvent -> object.add("hoverEvent", hoverEvent.toJson()));
        return object;
    }

    public static @Nullable TextSegment fromJson(@NotNull String jsonString) {
        return fromJson(JsonParser.parseString(jsonString).getAsJsonObject());
    }

    public static @Nullable TextSegment fromJson(@NotNull JsonObject jsonObject) {
        if (jsonObject.has("text")) {
            TextSegment textSegment = new TextSegment(jsonObject.get("text").getAsString());
            if (jsonObject.has("clickEvent")) textSegment.setClickEvent(Optional.of(ClickEvent.fromJson(jsonObject.get("clickEvent").getAsJsonObject())));
            if (jsonObject.has("hoverEvent")) textSegment.setHoverEvent(Optional.of(HoverEvent.fromJson(jsonObject.get("hoverEvent").getAsJsonObject())));
            if (jsonObject.has("color")) textSegment.setColor(ChatFormat.valueOf(jsonObject.get("color").getAsString().toUpperCase()));
            if (jsonObject.has("obfuscated")) textSegment.setObfuscated(jsonObject.get("obfuscated").getAsBoolean());
            if (jsonObject.has("italic")) textSegment.setItalic(jsonObject.get("italic").getAsBoolean());
            if (jsonObject.has("bold")) textSegment.setBold(jsonObject.get("bold").getAsBoolean());
            if (jsonObject.has("underlined")) textSegment.setUnderlined(jsonObject.get("underlined").getAsBoolean());
            if (jsonObject.has("strikethrough")) textSegment.setStrikethrough(jsonObject.get("strikethrough").getAsBoolean());

            return textSegment;
        }

        // invalid object
        return null;
    }

    /**
     * This function takes in a legacy text string and converts it into a {@link TextSegment}.
     * <p>
     * Legacy text strings use the {@link ChatFormat#SECTION_SYMBOL}. Many keyboards do not have this symbol however,
     * which is probably why it was chosen. To get around this, it is common practice to substitute
     * the symbol for another, then translate it later. Often '&' is used, but this can differ from person
     * to person. In case the string does not have a {@link ChatFormat#SECTION_SYMBOL}, the method also checks for the
     * {@param characterSubstitute}
     *
     * @param legacyText The text to make into an object
     * @param symbolSubstitute The character substitute
     * @return A TextObject representing the legacy text.
     */
    public static @NotNull LineSegment fromLegacy(@NotNull String legacyText, char symbolSubstitute) {
        return fromLegacyHandler(legacyText, symbolSubstitute, () -> new TextSegment(""));
    }

    public static class Builder implements ClassBuilder<TextSegment> {

        protected String text = "";
        protected Optional<ChatFormat> color = Optional.empty();
        protected boolean italic, bold, underlined, obfuscated, strikethrough;
        private Optional<ClickEvent> clickEvent = Optional.empty();
        private Optional<HoverEvent> hoverEvent = Optional.empty();

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

        public Builder withColor(@Nullable ChatFormat color) {
            return this.withColor(Optional.ofNullable(color));
        }

        public Builder withColor(@NotNull Optional<ChatFormat> color) {
            this.color = color.filter(ChatFormat::isColor);
            return this;
        }

        public Builder withClickEvent(@Nullable ClickEvent clickEvent) {
            return this.withClickEvent(Optional.ofNullable(clickEvent));
        }

        public Builder withClickEvent(@NotNull Optional<ClickEvent> clickEvent) {
            this.clickEvent = clickEvent;
            return this;
        }

        public Builder withHoverEvent(@Nullable HoverEvent hoverEvent) {
            return this.withHoverEvent(Optional.ofNullable(hoverEvent));
        }

        public Builder withHoverEvent(@NotNull Optional<HoverEvent> hoverEvent) {
            this.hoverEvent = hoverEvent;
            return this;
        }

        public Builder withText(@Nullable String text) {
            return this.withText(Optional.ofNullable(text));
        }

        public Builder withText(@NotNull Optional<String> text) {
            this.text = text.filter(s -> !s.isEmpty()).orElse("");
            return this;
        }

        @Override
        public @NotNull TextSegment build() {
            TextSegment textSegment = new TextSegment(this.text);
            textSegment.setClickEvent(this.clickEvent);
            textSegment.setHoverEvent(hoverEvent);
            textSegment.setColor(this.color);
            textSegment.setObfuscated(this.obfuscated);
            textSegment.setItalic(this.italic);
            textSegment.setBold(this.bold);
            textSegment.setUnderlined(this.underlined);
            textSegment.setStrikethrough(this.strikethrough);
            return textSegment;
        }
    }
}