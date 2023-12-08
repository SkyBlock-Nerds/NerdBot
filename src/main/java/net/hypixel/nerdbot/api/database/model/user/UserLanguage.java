package net.hypixel.nerdbot.api.database.model.user;

import org.jetbrains.annotations.Nullable;

public enum UserLanguage {
    ENGLISH("English", "english.json"),
    SPANISH("Español", "spanish.json");

    public static final UserLanguage[] VALUES = values();

    private final String name;
    private final String fileName;

    UserLanguage(String name, String fileName) {
        this.name = name;
        this.fileName = fileName;
    }

    public String getName() {
        return name;
    }

    public String getFileName() {
        return fileName;
    }

    @Nullable
    public static UserLanguage getLanguage(String name) {
        for (UserLanguage language : values()) {
            if (language.name().equalsIgnoreCase(name) || language.getName().equalsIgnoreCase(name)) {
                return language;
            }
        }

        return null;
    }
}
