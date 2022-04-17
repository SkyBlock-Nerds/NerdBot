package net.hypixel.nerdbot.config;

public class BotConfig {

    private int minimumThreshold;

    private double percentage;

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
                ", minimumThreshold=" + minimumThreshold +
                ", percentage=" + percentage +
                '}';
    }
}
