package net.hypixel.nerdbot.urlwatcher.handler.status;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.hypixel.nerdbot.bot.config.StatusPageConfig;
import net.hypixel.nerdbot.util.TimeUtils;

import java.awt.Color;
import java.time.Instant;
import java.util.Date;

@RequiredArgsConstructor
public class StatusPageEmbedFactory {

    private final StatusPageConfig config;

    public MessageEmbed createIncidentEmbed(StatusPageResponse.Incident incident, StatusPageEventType eventType) {
        EmbedBuilder builder = new EmbedBuilder();

        builder.setTitle(eventType.getEmoji() + " " + incident.getName(), incident.getShortlink());
        builder.setColor(getIncidentColor(incident.getImpact(), incident.getStatus()));

        StringBuilder description = new StringBuilder();
        StatusPageResponse.Incident.IncidentUpdate latestUpdate = incident.getLatestUpdate();

        if (latestUpdate != null && latestUpdate.getBody() != null) {
            String statusLabel = getCleanStatusLabel(incident.getStatus());
            description.append("**").append(statusLabel).append(":** ");

            String body = latestUpdate.getBody();
            if (body.length() > config.getMaxDescriptionLength()) {
                body = body.substring(0, config.getMaxDescriptionLength() - 3) + "...";
            }
            description.append(body);
        }

        builder.setDescription(description.toString());
        builder.setTimestamp(Instant.now());

        return builder.build();
    }

    public MessageEmbed createMaintenanceEmbed(StatusPageResponse.ScheduledMaintenance maintenance, StatusPageEventType eventType) {
        EmbedBuilder builder = new EmbedBuilder();

        builder.setTitle(eventType.getEmoji() + " " + maintenance.getName(), maintenance.getShortlink());
        builder.setColor(getMaintenanceColor(maintenance.getStatus()));

        StringBuilder description = new StringBuilder();
        if (maintenance.getIncidentUpdates() != null && !maintenance.getIncidentUpdates().isEmpty()) {
            StatusPageResponse.Incident.IncidentUpdate latestUpdate = maintenance.getIncidentUpdates().get(0);
            if (latestUpdate.getBody() != null) {
                String statusLabel = getCleanMaintenanceStatusLabel(maintenance.getStatus());
                description.append("**").append(statusLabel).append(":** ");

                String body = latestUpdate.getBody();
                if (body.length() > config.getMaxDescriptionLength()) {
                    body = body.substring(0, config.getMaxDescriptionLength() - 3) + "...";
                }
                description.append(body);
            }
        }

        if (maintenance.getScheduledFor() != null || maintenance.getScheduledUntil() != null) {
            description.append("\n\n");

            if (maintenance.getScheduledFor() != null) {
                description.append("**Scheduled For:** ").append(maintenance.getFormattedScheduledFor()).append("\n");
            }

            if (maintenance.getScheduledUntil() != null) {
                description.append("**Scheduled Until:** ").append(maintenance.getFormattedScheduledUntil()).append("\n");
            }

            if (maintenance.getScheduledFor() != null && maintenance.getScheduledUntil() != null) {
                long startTime = Date.from(maintenance.getScheduledForInstant()).getTime();
                long endTime = Date.from(maintenance.getScheduledUntilInstant()).getTime();
                long duration = endTime - startTime;

                description.append("**Estimated Duration:** ").append(TimeUtils.formatMsLong(duration)).append("\n");
            }
        }

        builder.setDescription(description.toString());
        builder.setTimestamp(Instant.now());

        return builder.build();
    }

    private String getCleanStatusLabel(String status) {
        return switch (status.toLowerCase()) {
            case "investigating" -> "Investigating";
            case "identified" -> "Identified";
            case "monitoring" -> "Monitoring";
            case "resolved" -> "Resolved";
            default -> "Unknown Status";
        };
    }

    private String getCleanMaintenanceStatusLabel(String status) {
        return switch (status.toLowerCase()) {
            case "scheduled" -> "Scheduled";
            case "in_progress" -> "In Progress";
            case "completed" -> "Completed";
            default -> "Unknown Status";
        };
    }

    private Color getIncidentColor(String impact, String status) {
        if (status.equalsIgnoreCase("resolved")) {
            return config.getOperationalColorObject();
        }

        return switch (impact != null ? impact.toLowerCase() : "none") {
            case "critical" -> config.getMajorOutageColorObject();
            case "minor" -> config.getDegradedColorObject();
            default -> config.getPartialOutageColorObject();
        };
    }

    private Color getMaintenanceColor(String status) {
        return status != null && status.equalsIgnoreCase("completed") ?
            config.getOperationalColorObject() : config.getMaintenanceColorObject();
    }
}