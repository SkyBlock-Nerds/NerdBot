package net.hypixel.nerdbot.api.database.model.user.language;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

@Getter
public enum UserLanguage {
    ENGLISH("English", "english.json"),
    DUTCH("Dutch", "dutch.json"),
    GERMAN("German", "german.json");

    public static final UserLanguage[] VALUES = values();

    private final String name;
    private final String fileName;

    UserLanguage(String name, String fileName) {
        this.name = name;
        this.fileName = fileName;
    }

    @Nullable
    public static UserLanguage getLanguage(String name) {
        for (UserLanguage language : VALUES) {
            if (language.name().equalsIgnoreCase(name) || language.getName().equalsIgnoreCase(name)) {
                return language;
            }
        }

        return null;
    }
}
