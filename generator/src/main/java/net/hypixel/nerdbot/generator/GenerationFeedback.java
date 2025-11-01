package net.hypixel.nerdbot.generator;

@FunctionalInterface
public interface GenerationFeedback {

    void sendMessage(String message, boolean ephemeral);

}