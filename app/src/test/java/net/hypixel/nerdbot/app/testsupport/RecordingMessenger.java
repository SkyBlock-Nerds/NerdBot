package net.hypixel.nerdbot.app.testsupport;

import net.hypixel.nerdbot.app.user.BirthdayAnnouncer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RecordingMessenger implements BirthdayAnnouncer.Messenger {

    private final List<String> messages = new ArrayList<>();

    @Override
    public CompletableFuture<?> send(String message) {
        messages.add(message);
        return CompletableFuture.completedFuture(null);
    }

    public List<String> messages() {
        return messages;
    }
}
