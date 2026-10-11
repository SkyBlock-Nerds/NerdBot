package net.hypixel.nerdbot.app.reminder;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.hypixel.nerdbot.marmalade.discord.EmbedFactory;
import net.hypixel.nerdbot.marmalade.format.DiscordTimestamp;
import net.hypixel.nerdbot.marmalade.storage.database.model.reminder.Reminder;

@Slf4j
public class DiscordReminderSender implements ReminderSender {

    private final JDA jda;

    @Inject
    public DiscordReminderSender(JDA jda) {
        this.jda = jda;
    }

    @Override
    public void sendDue(Reminder reminder, boolean late) {
        jda.retrieveUserById(reminder.getUserId())
            .flatMap(user -> user.openPrivateChannel()
                .flatMap(channel -> channel.sendMessage(dueMessage(user.getAsMention(), reminder.getTime(), late))
                    .addEmbeds(new EmbedBuilder().setDescription(reminder.getDescription()).build())))
            .queue(
                sent -> log.info("Sent reminder '{}' to user {}", reminder.getUuid(), reminder.getUserId()),
                failure -> log.warn("Couldn't send reminder '{}' to user {}", reminder.getUuid(), reminder.getUserId(), failure)
            );
    }

    @Override
    public void confirmCreated(Reminder reminder) {
        jda.retrieveUserById(reminder.getUserId())
            .flatMap(User::openPrivateChannel)
            .flatMap(channel -> channel.sendMessage("Reminder set for: " + DiscordTimestamp.toLongDateTime(reminder.getTime()))
                .addEmbeds(EmbedFactory.success(null, reminder.getDescription()).setFooter(reminder.getUuid().toString()).build())
                .setSuppressedNotifications(true))
            .queue(
                sent -> log.debug("Sent confirmation for reminder '{}'", reminder.getUuid()),
                failure -> log.warn("Couldn't send confirmation for reminder '{}' to user {}", reminder.getUuid(), reminder.getUserId(), failure)
            );
    }

    static String dueMessage(String mention, long time, boolean late) {
        String timestamp = DiscordTimestamp.toLongDateTime(time);
        return late
            ? mention + ", while I was offline, you asked me to remind you at " + timestamp + " about: "
            : mention + ", you asked me to remind you at " + timestamp + " about: ";
    }
}
