package net.hypixel.nerdbot.urlwatcher.handler.status;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.urlwatcher.URLWatcher;
import net.hypixel.nerdbot.bot.config.channel.ChannelConfig;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.TimeUtil;
import net.hypixel.nerdbot.util.Tuple;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Log4j2
public class StatusPageDataHandler implements URLWatcher.DataHandler {

    private static final Color COLOR_OPERATIONAL = new Color(0x00C851);
    private static final Color COLOR_DEGRADED = new Color(0xFFBB33);
    private static final Color COLOR_PARTIAL_OUTAGE = new Color(0xFF4444);
    private static final Color COLOR_MAJOR_OUTAGE = new Color(0x8B0000);
    private static final Color COLOR_MAINTENANCE = new Color(0x3498DB);
    private static final Color COLOR_INCIDENT = COLOR_PARTIAL_OUTAGE;

    private static final int MAX_DESCRIPTION_LENGTH = 200;

    @Override
    public void handleData(String oldContent, String newContent, List<Tuple<String, Object, Object>> changedValues) {
        ChannelConfig config = NerdBotApp.getBot().getConfig().getChannelConfig();

        log.info("Status page data changed!");

        ChannelCache.getTextChannelById(config.getAnnouncementChannelId()).ifPresentOrElse(textChannel -> {
            log.debug("Changed values: {}", changedValues);

            try {
                StatusPageResponse oldData = parseStatusData(oldContent);
                StatusPageResponse newData = parseStatusData(newContent);

                if (newData == null) {
                    log.warn("Failed to parse new status page data");
                    return;
                }

                List<MessageEmbed> embedsToSend = new ArrayList<>();
                boolean hasSignificantChanges = false;
                List<StatusPageResponse.Incident> newIncidents = findNewIncidents(oldData, newData);
                List<StatusPageResponse.Incident> updatedIncidents = findUpdatedIncidents(oldData, newData);

                for (StatusPageResponse.Incident incident : newIncidents) {
                    if (!incident.getStatus().equalsIgnoreCase("resolved")) {
                        embedsToSend.add(createIncidentEmbed(incident, true));
                        hasSignificantChanges = true;
                        log.info("Found new incident: {}", incident.getName());
                    }
                }

                for (StatusPageResponse.Incident incident : updatedIncidents) {
                    embedsToSend.add(createIncidentEmbed(incident, false));
                    hasSignificantChanges = true;
                    log.info("Found updated incident: {}", incident.getName());
                }

                List<StatusPageResponse.ScheduledMaintenance> newMaintenances = findNewMaintenances(oldData, newData);
                List<StatusPageResponse.ScheduledMaintenance> updatedMaintenances = findUpdatedMaintenances(oldData, newData);

                for (StatusPageResponse.ScheduledMaintenance maintenance : newMaintenances) {
                    embedsToSend.add(createMaintenanceEmbed(maintenance, true));
                    hasSignificantChanges = true;
                    log.info("Found new maintenance: {}", maintenance.getName());
                }

                for (StatusPageResponse.ScheduledMaintenance maintenance : updatedMaintenances) {
                    embedsToSend.add(createMaintenanceEmbed(maintenance, false));
                    hasSignificantChanges = true;
                    log.info("Found updated maintenance: {}", maintenance.getName());
                }

                if (hasSignificantChanges && !embedsToSend.isEmpty()) {
                    MessageCreateBuilder messageBuilder = new MessageCreateBuilder();

                    if (!newIncidents.isEmpty() || !updatedIncidents.isEmpty()) {
                        RoleManager.getPingableRoleByName("Status Alerts").ifPresent(pingableRole -> {
                            messageBuilder.addContent(RoleManager.formatPingableRoleAsMention(pingableRole) + "\n\n");
                        });
                    }

                    messageBuilder.setEmbeds(embedsToSend);
                    textChannel.sendMessage(messageBuilder.build()).queue();
                    log.info("Sent {} status embeds to Discord", embedsToSend.size());
                } else {
                    log.debug("No significant status changes detected");
                }
            } catch (Exception e) {
                log.error("Error processing status page data", e);
            }
        }, () -> log.warn("Announcement channel not found!"));
    }

    private StatusPageResponse parseStatusData(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        try {
            return NerdBotApp.GSON.fromJson(content, StatusPageResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse status page data", e);
            return null;
        }
    }

    private List<StatusPageResponse.Incident> findNewIncidents(StatusPageResponse oldData, StatusPageResponse newData) {
        if (newData.getIncidents() == null || newData.getIncidents().isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> oldIncidentIds = new HashSet<>();
        if (oldData != null && oldData.getIncidents() != null) {
            oldData.getIncidents().forEach(incident -> oldIncidentIds.add(incident.getId()));
        }

        return newData.getIncidents().stream()
            .filter(incident -> !oldIncidentIds.contains(incident.getId()))
            .toList();
    }

    private List<StatusPageResponse.Incident> findUpdatedIncidents(StatusPageResponse oldData, StatusPageResponse newData) {
        if (oldData == null || oldData.getIncidents() == null || newData.getIncidents() == null) {
            return new ArrayList<>();
        }

        return newData.getIncidents().stream()
            .filter(newIncident -> {
                return oldData.getIncidents().stream()
                    .anyMatch(oldIncident -> oldIncident.getId().equals(newIncident.getId())
                        && hasIncidentChanged(oldIncident, newIncident));
            }).toList();
    }

    private List<StatusPageResponse.ScheduledMaintenance> findNewMaintenances(StatusPageResponse oldData, StatusPageResponse newData) {
        if (newData.getScheduledMaintenances() == null || newData.getScheduledMaintenances().isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> oldMaintenanceIds = new HashSet<>();
        if (oldData != null && oldData.getScheduledMaintenances() != null) {
            oldData.getScheduledMaintenances().forEach(maintenance -> oldMaintenanceIds.add(maintenance.getId()));
        }

        return newData.getScheduledMaintenances().stream()
            .filter(maintenance -> !oldMaintenanceIds.contains(maintenance.getId()))
            .toList();
    }

    private List<StatusPageResponse.ScheduledMaintenance> findUpdatedMaintenances(StatusPageResponse oldData, StatusPageResponse newData) {
        if (oldData == null || oldData.getScheduledMaintenances() == null || newData.getScheduledMaintenances() == null) {
            return new ArrayList<>();
        }

        return newData.getScheduledMaintenances().stream()
            .filter(newMaintenance -> {
                return oldData.getScheduledMaintenances().stream()
                    .anyMatch(oldMaintenance -> oldMaintenance.getId().equals(newMaintenance.getId())
                        && hasMaintenanceChanged(oldMaintenance, newMaintenance));
            }).toList();
    }

    private boolean hasIncidentChanged(StatusPageResponse.Incident oldIncident, StatusPageResponse.Incident newIncident) {
        if (!oldIncident.getStatus().equals(newIncident.getStatus())) {
            return true;
        }

        int oldUpdateCount = oldIncident.getIncidentUpdates() != null ? oldIncident.getIncidentUpdates().size() : 0;
        int newUpdateCount = newIncident.getIncidentUpdates() != null ? newIncident.getIncidentUpdates().size() : 0;

        return newUpdateCount > oldUpdateCount;
    }

    private boolean hasMaintenanceChanged(StatusPageResponse.ScheduledMaintenance oldMaintenance, StatusPageResponse.ScheduledMaintenance newMaintenance) {
        if (!oldMaintenance.getStatus().equals(newMaintenance.getStatus())) {
            return true;
        }

        int oldUpdateCount = oldMaintenance.getIncidentUpdates() != null ? oldMaintenance.getIncidentUpdates().size() : 0;
        int newUpdateCount = newMaintenance.getIncidentUpdates() != null ? newMaintenance.getIncidentUpdates().size() : 0;

        return newUpdateCount > oldUpdateCount;
    }

    private MessageEmbed createIncidentEmbed(StatusPageResponse.Incident incident, boolean isNew) {
        EmbedBuilder builder = new EmbedBuilder();
        String statusEmoji = incident.getStatus().equalsIgnoreCase("resolved") ? "✅" : "🛑";

        builder.setTitle(statusEmoji + " " + incident.getName(), incident.getShortlink());
        builder.setColor(getIncidentColor(incident.getImpact(), incident.getStatus()));

        StringBuilder description = new StringBuilder();
        StatusPageResponse.Incident.IncidentUpdate latestUpdate = incident.getLatestUpdate();

        if (latestUpdate != null && latestUpdate.getBody() != null) {
            String statusLabel = getCleanStatusLabel(incident.getStatus());
            description.append("**").append(statusLabel).append(":** ");

            String body = latestUpdate.getBody();
            if (body.length() > MAX_DESCRIPTION_LENGTH) {
                body = body.substring(0, MAX_DESCRIPTION_LENGTH - 3) + "...";
            }
            description.append(body);
        }

        builder.setDescription(description.toString());

        if (incident.getStatus().equalsIgnoreCase("resolved") && incident.getFormattedResolvedAt() != null) {
            builder.appendDescription("\n\n**Resolved:** " + incident.getFormattedResolvedAt());
        }

        builder.setTimestamp(Instant.now());

        return builder.build();
    }

    private MessageEmbed createMaintenanceEmbed(StatusPageResponse.ScheduledMaintenance maintenance, boolean isNew) {
        EmbedBuilder builder = new EmbedBuilder();

        builder.setTitle("🔧 " + maintenance.getName(), maintenance.getShortlink());
        builder.setColor(getMaintenanceColor(maintenance.getStatus()));

        StringBuilder description = new StringBuilder();
        if (maintenance.getIncidentUpdates() != null && !maintenance.getIncidentUpdates().isEmpty()) {
            StatusPageResponse.Incident.IncidentUpdate latestUpdate = maintenance.getIncidentUpdates().get(0);
            if (latestUpdate.getBody() != null) {
                String statusLabel = getCleanMaintenanceStatusLabel(maintenance.getStatus());
                description.append("**").append(statusLabel).append(":** ");

                String body = latestUpdate.getBody();
                if (body.length() > MAX_DESCRIPTION_LENGTH) {
                    body = body.substring(0, MAX_DESCRIPTION_LENGTH - 3) + "...";
                }
                description.append(body);
            }
        }

        if (maintenance.getFormattedScheduledFor() != null || maintenance.getFormattedScheduledUntil() != null) {
            description.append("\n\n");

            if (maintenance.getFormattedScheduledFor() != null) {
                description.append("**Scheduled For:** ").append(maintenance.getFormattedScheduledFor()).append("\n");
            }

            if (maintenance.getFormattedScheduledUntil() != null) {
                description.append("**Scheduled Until:** ").append(maintenance.getFormattedScheduledUntil()).append("\n");
            }

            if (maintenance.getFormattedScheduledFor() != null && maintenance.getFormattedScheduledUntil() != null) {
                long startTime = Date.from(maintenance.getScheduledForInstant()).getTime();
                long endTime = Date.from(maintenance.getScheduledUntilInstant()).getTime();
                long duration = endTime - startTime;

                description.append("**Estimated Duration:** ").append(TimeUtil.formatMsLong(duration)).append("\n");
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
            default -> "???";
        };
    }

    private String getCleanMaintenanceStatusLabel(String status) {
        return switch (status.toLowerCase()) {
            case "scheduled" -> "Scheduled";
            case "in_progress" -> "In Progress";
            case "completed" -> "Completed";
            default -> "???";
        };
    }

    private Color getIncidentColor(String impact, String status) {
        if (status.equalsIgnoreCase("resolved")) {
            return COLOR_OPERATIONAL;
        }

        return switch (impact != null ? impact.toLowerCase() : "none") {
            case "critical" -> COLOR_MAJOR_OUTAGE;
            case "major" -> COLOR_PARTIAL_OUTAGE;
            case "minor" -> COLOR_DEGRADED;
            default -> COLOR_INCIDENT;
        };
    }

    private Color getMaintenanceColor(String status) {
        return status != null && status.equalsIgnoreCase("completed") ?
            COLOR_OPERATIONAL : COLOR_MAINTENANCE;
    }
}