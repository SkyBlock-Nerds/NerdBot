package net.hypixel.nerdbot.channel;

public enum Reactions {
    AGREE("965225847306981417"),
    DISAGREE("965225847378313256"),
    GREENLIT("965226114005999626");

    private final String id;

    Reactions(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
