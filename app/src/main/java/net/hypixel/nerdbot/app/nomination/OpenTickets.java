package net.hypixel.nerdbot.app.nomination;

import java.util.List;

/** A member's open TicketBot tickets, shown on their inactivity warnings. */
public interface OpenTickets {

    /** Links such as {@code #0001: <#channelId>}, newest first; empty when they can't be looked up. */
    List<String> linksFor(String memberId);
}
