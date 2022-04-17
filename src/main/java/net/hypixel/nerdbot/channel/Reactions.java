package net.hypixel.nerdbot.channel;

public enum Reactions {
    AGREE("yes"),
    DISAGREE("no"),
    GREENLIT("greenlit");

    private final String reaction;

    Reactions(String reaction) {
        this.reaction = reaction;
    }

    public String getReaction() {
        return reaction;
    }
}
