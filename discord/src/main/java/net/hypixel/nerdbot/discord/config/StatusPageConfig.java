package net.hypixel.nerdbot.discord.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.awt.*;

@Getter
@Setter
@ToString
public class StatusPageConfig {

    /**
     * The color used for operational status (green)
     */
    private String operationalColor = "00C851";

    /**
     * The color used for degraded performance (orange)
     */
    private String degradedColor = "FFBB33";

    /**
     * The color used for partial outages (red)
     */
    private String partialOutageColor = "FF4444";

    /**
     * The color used for major outages (dark red)
     */
    private String majorOutageColor = "8B0000";

    /**
     * The color used for maintenance (blue)
     */
    private String maintenanceColor = "3498DB";

    /**
     * The maximum length of status descriptions in embeds
     */
    private int maxDescriptionLength = 200;

    /**
     * Whether to include resolved incidents in notifications
     */
    private boolean includeResolvedIncidents = true;

    /**
     * Whether to include completed maintenances in notifications
     */
    private boolean includeCompletedMaintenances = true;

    /**
     * Whether to enable status alert pings for incidents
     */
    private boolean enableStatusAlerts = true;

    /**
     * The name of the role to ping for status alerts
     */
    private String statusAlertRoleName = "Status Alerts";

    /**
     * Whether to enable alerts for maintenance events
     */
    private boolean enableMaintenanceAlerts = false;

    /**
     * Converts a hex color string to a Color object
     *
     * @param hexColor hex color string (e.g., "FF0000" for red)
     *
     * @return Color object
     */
    public static Color hexToColor(String hexColor) {
        if (hexColor == null || hexColor.isEmpty()) {
            return Color.BLACK;
        }

        try {
            if (hexColor.startsWith("#")) {
                hexColor = hexColor.substring(1);
            }

            return new Color(Integer.parseInt(hexColor, 16));
        } catch (NumberFormatException e) {
            return Color.BLACK;
        }
    }

    /**
     * Gets the operational color as a Color object
     */
    public Color getOperationalColorObject() {
        return hexToColor(operationalColor);
    }

    /**
     * Gets the degraded color as a Color object
     */
    public Color getDegradedColorObject() {
        return hexToColor(degradedColor);
    }

    /**
     * Gets the partial outage color as a Color object
     */
    public Color getPartialOutageColorObject() {
        return hexToColor(partialOutageColor);
    }

    /**
     * Gets the major outage color as a Color object
     */
    public Color getMajorOutageColorObject() {
        return hexToColor(majorOutageColor);
    }

    /**
     * Gets the maintenance color as a Color object
     */
    public Color getMaintenanceColorObject() {
        return hexToColor(maintenanceColor);
    }
}