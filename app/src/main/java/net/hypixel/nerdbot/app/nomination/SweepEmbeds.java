package net.hypixel.nerdbot.app.nomination;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.hypixel.nerdbot.discord.util.StringUtils;

import java.util.List;

/** The parts nominations and inactivity warnings share. */
final class SweepEmbeds {

    // Space kept for the "...and N more" line when not every ticket fits
    private static final int OVERFLOW_ROOM = 32;

    private SweepEmbeds() {
    }

    /** The summary, then the messages, votes and comments against what's required. */
    static EmbedBuilder addProgress(EmbedBuilder embed, ActivityRequirements.Progress progress, String extraSummaryLine) {
        ActivityRequirements requirements = progress.requirements();
        String summary = String.format("**Requirements Met:** %d/3", progress.met());
        if (extraSummaryLine != null) {
            summary += "\n" + extraSummaryLine;
        }

        return embed
            .addField(String.format("Activity Summary (last %d days)", requirements.windowDays()), summary, false)
            .addField("Messages", requirementLine(progress.messages(), requirements.requiredMessages()), true)
            .addField("Votes", requirementLine(progress.votes(), requirements.requiredVotes()), true)
            .addField("Comments", requirementLine(progress.comments(), requirements.requiredComments()), true);
    }

    static String requirementLine(int actual, int required) {
        return String.format("%s **%s** / %s required",
            actual >= required ? "✅" : "❌",
            StringUtils.formatWithCommas(actual),
            StringUtils.formatWithCommas(required));
    }

    /** Lists as many tickets as fit in a field, then how many more there are. */
    static void addOpenTickets(EmbedBuilder embed, List<String> links) {
        if (links.isEmpty()) {
            return;
        }

        String all = String.join("\n", links);
        if (all.length() <= MessageEmbed.VALUE_MAX_LENGTH) {
            embed.addField("Open Tickets", all, false);
            return;
        }

        StringBuilder value = new StringBuilder();
        int shown = 0;
        for (String link : links) {
            if (value.length() + link.length() + 1 > MessageEmbed.VALUE_MAX_LENGTH - OVERFLOW_ROOM) {
                break;
            }
            value.append(link).append('\n');
            shown++;
        }
        value.append("...and ").append(links.size() - shown).append(" more");
        embed.addField("Open Tickets", value.toString(), false);
    }
}
