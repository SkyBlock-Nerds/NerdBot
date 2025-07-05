package net.hypixel.nerdbot.urlwatcher.handler.status;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import net.hypixel.nerdbot.util.discord.DiscordTimestamp;

import java.time.Instant;
import java.util.List;

@Data
public class StatusPageResponse {
    private Page page;
    private List<Component> components;
    private List<Incident> incidents;
    @SerializedName("scheduled_maintenances")
    private List<ScheduledMaintenance> scheduledMaintenances;
    private Status status;

    @Data
    public static class Page {
        private String id;
        private String name;
        private String url;
        @SerializedName("time_zone")
        private String timeZone;
        @SerializedName("updated_at")
        private String updatedAt;
    }

    @Data
    public static class Component {
        private String id;
        private String name;
        private String status;
        @SerializedName("created_at")
        private String createdAt;
        @SerializedName("updated_at")
        private String updatedAt;
        private Integer position;
        private String description;
        private boolean showcase;
        @SerializedName("start_date")
        private String startDate;
        @SerializedName("group_id")
        private String groupId;
        @SerializedName("page_id")
        private String pageId;
        private boolean group;
        @SerializedName("only_show_if_degraded")
        private boolean onlyShowIfDegraded;
        private List<String> components;
    }

    @Data
    public static class Incident {
        private String id;
        private String name;
        private String status;
        @SerializedName("created_at")
        private String createdAt;
        @SerializedName("updated_at")
        private String updatedAt;
        @SerializedName("monitoring_at")
        private String monitoringAt;
        @SerializedName("resolved_at")
        private String resolvedAt;
        private String impact;
        private String shortlink;
        @SerializedName("started_at")
        private String startedAt;
        @SerializedName("page_id")
        private String pageId;
        @SerializedName("incident_updates")
        private List<IncidentUpdate> incidentUpdates;
        private List<Component> components;

        public IncidentUpdate getLatestUpdate() {
            return incidentUpdates != null && !incidentUpdates.isEmpty()
                ? incidentUpdates.get(0)
                : null;
        }

        @Data
        public static class IncidentUpdate {
            private String id;
            private String status;
            private String body;
            @SerializedName("incident_id")
            private String incidentId;
            @SerializedName("created_at")
            private String createdAt;
            @SerializedName("updated_at")
            private String updatedAt;
            @SerializedName("display_at")
            private String displayAt;
            @SerializedName("affected_components")
            private List<AffectedComponent> affectedComponents;
            @SerializedName("deliver_notifications")
            private boolean deliverNotifications;
            @SerializedName("custom_tweet")
            private String customTweet;
            @SerializedName("tweet_id")
            private String tweetId;

            @Data
            public static class AffectedComponent {
                private String code;
                private String name;
                @SerializedName("old_status")
                private String oldStatus;
                @SerializedName("new_status")
                private String newStatus;
            }
        }
    }

    @Data
    public static class ScheduledMaintenance {
        private String id;
        private String name;
        private String status;
        @SerializedName("created_at")
        private String createdAt;
        @SerializedName("updated_at")
        private String updatedAt;
        @SerializedName("monitoring_at")
        private String monitoringAt;
        @SerializedName("resolved_at")
        private String resolvedAt;
        private String impact;
        private String shortlink;
        @SerializedName("started_at")
        private String startedAt;
        @SerializedName("page_id")
        private String pageId;
        @SerializedName("incident_updates")
        private List<Incident.IncidentUpdate> incidentUpdates;
        private List<Component> components;
        @SerializedName("scheduled_for")
        private String scheduledFor;
        @SerializedName("scheduled_until")
        private String scheduledUntil;

        public Instant getScheduledForInstant() {
            return scheduledFor != null ? Instant.parse(scheduledFor) : null;
        }

        public Instant getScheduledUntilInstant() {
            return scheduledUntil != null ? Instant.parse(scheduledUntil) : null;
        }

        public String getFormattedScheduledFor() {
            return DiscordTimestamp.toLongDateTime(Instant.parse(scheduledFor).toEpochMilli());
        }

        public String getFormattedScheduledUntil() {
            return DiscordTimestamp.toLongDateTime(Instant.parse(scheduledUntil).toEpochMilli());
        }
    }

    @Data
    public static class Status {
        private String indicator;
        private String description;
    }
}