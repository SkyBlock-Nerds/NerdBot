package net.hypixel.nerdbot.util.skyblock;

public enum Gemstone {
    GEM_RUBY("[❤]"),
    GEM_AMETHYST("[❈]"),
    GEM_OPAL("[❂]"),
    GEM_SAPPHIRE("[✎]"),
    GEM_JASPER("[❁]"),
    GEM_JADE("[☘]"),
    GEM_AMBER("[⸕]"),
    GEM_TOPAZ("[✧]"),
    GEM_COMBAT("[⚔]"),
    GEM_OFFENSIVE("[☠]"),
    GEM_DEFENSIVE("[☤]"),
    GEM_MINING("[✦]"),
    GEM_UNIVERSAL("[❂]");

    public static final Gemstone[] VALUES = values();

    private final String gemstone;

    Gemstone(String gemstone) {
        this.gemstone = gemstone;
    }

    public String getId() {
        return gemstone;
    }
}
