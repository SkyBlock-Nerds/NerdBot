package net.hypixel.nerdbot.api.config;

public class Emojis {

    private final String agree, disagree, greenlit;

    public Emojis(String agree, String disagree, String greenlit) {
        this.agree = agree;
        this.disagree = disagree;
        this.greenlit = greenlit;
    }

    public String getAgree() {
        return agree;
    }

    public String getDisagree() {
        return disagree;
    }

    public String getGreenlit() {
        return greenlit;
    }
}
