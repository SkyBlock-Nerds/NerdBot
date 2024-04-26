package net.hypixel.skyblocknerds.api.badge;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@ToString
public class Badge {

    private final String id;
    private final String name;
    private final String emoji;

    public String getEmoji() {
        // TODO: Implement

        return emoji;
    }

    public String getFormattedName() {
        // TODO: Implement
        //  return getEmoji().getFormatted() + " " + name;

        return name;
    }
}