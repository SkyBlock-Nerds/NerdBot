package net.hypixel.nerdbot.urlwatcher.handler.status;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.config.StatusPageConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class StatusPageChangeDetector {

    private final StatusPageConfig config;

    public List<StatusPageResponse.Incident> findNewIncidents(StatusPageResponse oldData, StatusPageResponse newData) {
        if (newData.getIncidents() == null || newData.getIncidents().isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> oldIncidentIds = new HashSet<>();
        if (oldData != null && oldData.getIncidents() != null) {
            oldData.getIncidents().forEach(incident -> oldIncidentIds.add(incident.getId()));
        }

        List<StatusPageResponse.Incident> newIncidents = newData.getIncidents().stream()
            .filter(incident -> !oldIncidentIds.contains(incident.getId()))
            .toList();

        if (config.isIncludeResolvedIncidents()) {
            return newIncidents;
        }

        return newIncidents.stream()
            .filter(incident -> !incident.getStatus().equalsIgnoreCase("resolved"))
            .toList();
    }

    public List<StatusPageResponse.Incident> findUpdatedIncidents(StatusPageResponse oldData, StatusPageResponse newData) {
        if (oldData == null || oldData.getIncidents() == null || newData.getIncidents() == null) {
            return new ArrayList<>();
        }

        List<StatusPageResponse.Incident> updatedIncidents = newData.getIncidents().stream()
            .filter(newIncident -> {
                return oldData.getIncidents().stream()
                    .anyMatch(oldIncident -> oldIncident.getId().equals(newIncident.getId())
                        && hasIncidentChanged(oldIncident, newIncident));
            }).toList();

        if (config.isIncludeResolvedIncidents()) {
            return updatedIncidents;
        }

        return updatedIncidents.stream()
            .filter(incident -> !incident.getStatus().equalsIgnoreCase("resolved"))
            .toList();
    }

    public List<StatusPageResponse.ScheduledMaintenance> findNewMaintenances(StatusPageResponse oldData, StatusPageResponse newData) {
        if (newData.getScheduledMaintenances() == null || newData.getScheduledMaintenances().isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> oldMaintenanceIds = new HashSet<>();
        if (oldData != null && oldData.getScheduledMaintenances() != null) {
            oldData.getScheduledMaintenances().forEach(maintenance -> oldMaintenanceIds.add(maintenance.getId()));
        }

        List<StatusPageResponse.ScheduledMaintenance> newMaintenances = newData.getScheduledMaintenances().stream()
            .filter(maintenance -> !oldMaintenanceIds.contains(maintenance.getId()))
            .toList();

        if (config.isIncludeCompletedMaintenances()) {
            return newMaintenances;
        }

        return newMaintenances.stream()
            .filter(maintenance -> !maintenance.getStatus().equalsIgnoreCase("completed"))
            .toList();
    }

    public List<StatusPageResponse.ScheduledMaintenance> findUpdatedMaintenances(StatusPageResponse oldData, StatusPageResponse newData) {
        if (oldData == null || oldData.getScheduledMaintenances() == null || newData.getScheduledMaintenances() == null) {
            return new ArrayList<>();
        }

        List<StatusPageResponse.ScheduledMaintenance> updatedMaintenances = newData.getScheduledMaintenances().stream()
            .filter(newMaintenance -> {
                return oldData.getScheduledMaintenances().stream()
                    .anyMatch(oldMaintenance -> oldMaintenance.getId().equals(newMaintenance.getId())
                        && hasMaintenanceChanged(oldMaintenance, newMaintenance));
            }).toList();

        if (config.isIncludeCompletedMaintenances()) {
            return updatedMaintenances;
        }

        return updatedMaintenances.stream()
            .filter(maintenance -> !maintenance.getStatus().equalsIgnoreCase("completed"))
            .toList();
    }

    public boolean hasIncidentChanged(StatusPageResponse.Incident oldIncident, StatusPageResponse.Incident newIncident) {
        if (!oldIncident.getStatus().equals(newIncident.getStatus())) {
            log.debug("Incident {} status changed from {} to {}",
                newIncident.getId(), oldIncident.getStatus(), newIncident.getStatus());
            return true;
        }

        int oldUpdateCount = oldIncident.getIncidentUpdates() != null ? oldIncident.getIncidentUpdates().size() : 0;
        int newUpdateCount = newIncident.getIncidentUpdates() != null ? newIncident.getIncidentUpdates().size() : 0;

        if (newUpdateCount > oldUpdateCount) {
            log.debug("Incident {} has {} new updates",
                newIncident.getId(), newUpdateCount - oldUpdateCount);
            return true;
        }

        return false;
    }

    public boolean hasMaintenanceChanged(StatusPageResponse.ScheduledMaintenance oldMaintenance, StatusPageResponse.ScheduledMaintenance newMaintenance) {
        if (!oldMaintenance.getStatus().equals(newMaintenance.getStatus())) {
            log.debug("Maintenance {} status changed from {} to {}",
                newMaintenance.getId(), oldMaintenance.getStatus(), newMaintenance.getStatus());
            return true;
        }

        int oldUpdateCount = oldMaintenance.getIncidentUpdates() != null ? oldMaintenance.getIncidentUpdates().size() : 0;
        int newUpdateCount = newMaintenance.getIncidentUpdates() != null ? newMaintenance.getIncidentUpdates().size() : 0;

        if (newUpdateCount > oldUpdateCount) {
            log.debug("Maintenance {} has {} new updates",
                newMaintenance.getId(), newUpdateCount - oldUpdateCount);
            return true;
        }

        return false;
    }
}