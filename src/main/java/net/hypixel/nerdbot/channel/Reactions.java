package net.hypixel.nerdbot.channel;

public enum Reactions {
    AGREE("965225847306981417"),
    DISAGREE("965225847378313256"),
    GREENLIT("965226114005999626");

    public static final String THUMBS_UP_EMOJI = "\uD83D\uDC4D";

    public static final String THUMBS_DOWN_EMOJI = "\uD83D\uDC4E";

    private final String id;

    Reactions(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
