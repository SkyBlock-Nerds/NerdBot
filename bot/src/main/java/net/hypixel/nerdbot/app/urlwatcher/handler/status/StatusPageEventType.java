package net.hypixel.nerdbot.app.urlwatcher.handler.status;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StatusPageEventType {
    NEW_INCIDENT("🛑", "New Incident"),
    INCIDENT_UPDATE("🔄", "Incident Update"),
    INCIDENT_RESOLVED("✅", "Incident Resolved"),
    NEW_MAINTENANCE("🔧", "Scheduled Maintenance"),
    MAINTENANCE_UPDATE("🔄", "Maintenance Update"),
    MAINTENANCE_COMPLETED("✅", "Maintenance Completed"),
    COMPONENT_STATUS_CHANGE("⚠️", "Component Status Change");

    private final String emoji;
    private final String displayName;

    public static StatusPageEventType fromIncidentStatus(String status, boolean isNew) {
        if (isNew) {
            return status.equalsIgnoreCase("resolved") ? INCIDENT_RESOLVED : NEW_INCIDENT;
        }

        return status.equalsIgnoreCase("resolved") ? INCIDENT_RESOLVED : INCIDENT_UPDATE;
    }

    public static StatusPageEventType fromMaintenanceStatus(String status, boolean isNew) {
        if (isNew) {
            return status.equalsIgnoreCase("completed") ? MAINTENANCE_COMPLETED : NEW_MAINTENANCE;
        }

        return status.equalsIgnoreCase("completed") ? MAINTENANCE_COMPLETED : MAINTENANCE_UPDATE;
    }
}