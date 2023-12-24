package net.hypixel.nerdbot.generator.util;

import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
public class ColoredString {
    @Getter(AccessLevel.NONE)
    private final StringBuilder currentString;
    private MCColor currentColor;
    private boolean bold;
    private boolean italic;
    private boolean strikethrough;
    private boolean underlined;

    public ColoredString() {
        this.currentString = new StringBuilder(36);
        this.currentColor = MCColor.GRAY;
    }

    public ColoredString(@NotNull ColoredString previousColoredString) {
        this();
        this.setCurrentColor(previousColoredString.currentColor);
        this.setBold(previousColoredString.bold);
        this.setItalic(previousColoredString.italic);
    }

    public boolean isEmpty() {
        return this.currentString.toString().isBlank();
    }

    public void addString(String newString) {
        this.currentString.append(newString);
    }

    public boolean hasSpecialFormatting() {
        return this.bold || this.italic || this.strikethrough || this.underlined;
    }

    @Override
    public String toString() {
        return this.currentString.toString();
    }

    public JsonObject convertToJson() {
        JsonObject json = new JsonObject();

        json.addProperty("text", this.currentString.toString());
        json.addProperty("color", this.currentColor.name().toLowerCase());

        if (this.bold) {
            json.addProperty("bold", true);
        }

        if (this.italic) {
            json.addProperty("italic", true);
        }

        if (this.strikethrough) {
            json.addProperty("strikethrough", true);
        }

        if (this.underlined) {
            json.addProperty("underlined", true);
        }

        return json;
    }
}