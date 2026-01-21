package net.hypixel.nerdbot.discord.config.channel;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TicketReminderThreshold {

    /**
     * Hours without a response before this reminder triggers
     */
    private int hoursWithoutResponse;

    /**
     * Message to post in the ticket thread
     */
    private String message;

    /**
     * Whether to ping the ticket role with this reminder
     */
    private boolean pingStaff;
}