package net.hypixel.nerdbot.api.badge;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@RequiredArgsConstructor
public class Badge {
    private final String id;
    private final String name;
    private final String emoji;

    public String getFormattedName() {
        if (emoji == null || emoji.isBlank()) {
            return name;
        }
        return emoji + " " + name;
    }
}