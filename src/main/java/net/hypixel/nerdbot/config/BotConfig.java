package net.hypixel.nerdbot.config;

public class BotConfig {

    private String prefix;

    private int minimumThreshold;

    private double percentage;

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public int getMinimumThreshold() {
        return minimumThreshold;
    }

    public void setMinimumThreshold(int minimumThreshold) {
        this.minimumThreshold = minimumThreshold;
    }

    public double getPercentage() {
        return percentage;
    }

    public void setPercentage(double percentage) {
        this.percentage = percentage;
    }

    @Override
    public String toString() {
        return "BotConfig{" +
                "prefix='" + prefix + '\'' +
                ", minimumThreshold=" + minimumThreshold +
                ", percentage=" + percentage +
                '}';
    }
}
