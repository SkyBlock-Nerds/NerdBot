package net.hypixel.nerdbot.channel;

public enum Reactions {
    AGREE("yes"),
    DISAGREE("no"),
    GREENLIT("greenlit");

    private final String name;

    Reactions(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
