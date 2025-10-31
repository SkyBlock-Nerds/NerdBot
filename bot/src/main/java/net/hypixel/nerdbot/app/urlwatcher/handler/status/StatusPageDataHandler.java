package net.hypixel.nerdbot.app.urlwatcher.handler.status;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.hypixel.nerdbot.core.BotEnvironment;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.discord.config.StatusPageConfig;
import net.hypixel.nerdbot.discord.config.channel.ChannelConfig;
import net.hypixel.nerdbot.discord.cache.ChannelCache;
import net.hypixel.nerdbot.app.role.RoleManager;
import net.hypixel.nerdbot.app.urlwatcher.URLWatcher;
import net.hypixel.nerdbot.core.util.Tuple;

import java.util.List;
import java.util.stream.Stream;

import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

@Slf4j
public class StatusPageDataHandler implements URLWatcher.DataHandler {

    private final StatusPageConfig config;
    private final StatusPageChangeDetector changeDetector;
    private final StatusPageEmbedFactory embedFactory;

    public StatusPageDataHandler() {
        this.config = SkyBlockNerdsBot.config().getStatusPageConfig();
        this.changeDetector = new StatusPageChangeDetector(config);
        this.embedFactory = new StatusPageEmbedFactory(config);
    }

    public StatusPageDataHandler(StatusPageConfig config) {
        this.config = config;
        this.changeDetector = new StatusPageChangeDetector(config);
        this.embedFactory = new StatusPageEmbedFactory(config);
    }

    @Override
    public void handleData(String oldContent, String newContent, List<Tuple<String, Object, Object>> changedValues) {
        ChannelConfig channelConfig = DiscordBotEnvironment.getBot().getConfig().getChannelConfig();

        log.info("Status page data changed!");

        ChannelCache.getTextChannelById(channelConfig.getAnnouncementChannelId()).ifPresentOrElse(textChannel -> {
            log.debug("Changed values: {}", changedValues);

            try {
                StatusPageResponse oldData = parseStatusData(oldContent);
                StatusPageResponse newData = parseStatusData(newContent);

                if (newData == null) {
                    log.warn("Failed to parse new status page data");
                    return;
                }

                List<MessageEmbed> embedsToSend = Stream.of(
                    processIncidents(oldData, newData),
                    processMaintenances(oldData, newData)
                ).flatMap(List::stream).toList();

                boolean shouldPingStatusAlerts = hasIncidentChanges(oldData, newData) && config.isEnableStatusAlerts() ||
                    hasMaintenanceChanges(oldData, newData) && config.isEnableMaintenanceAlerts();

                if (!embedsToSend.isEmpty()) {
                    sendNotification(embedsToSend, shouldPingStatusAlerts, textChannel);
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
            return BotEnvironment.GSON.fromJson(content, StatusPageResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse status page data", e);
            return null;
        }
    }

    private List<MessageEmbed> processIncidents(StatusPageResponse oldData, StatusPageResponse newData) {
        return Stream.concat(
            processNewIncidents(oldData, newData),
            processUpdatedIncidents(oldData, newData)
        ).toList();
    }

    private List<MessageEmbed> processMaintenances(StatusPageResponse oldData, StatusPageResponse newData) {
        return Stream.concat(
            processNewMaintenances(oldData, newData),
            processUpdatedMaintenances(oldData, newData)
        ).toList();
    }

    private Stream<MessageEmbed> processNewIncidents(StatusPageResponse oldData, StatusPageResponse newData) {
        return changeDetector.findNewIncidents(oldData, newData).stream()
            .peek(incident -> log.info("Found new incident: {} ({})", incident.getName(), incident.getStatus()))
            .map(incident -> {
                StatusPageEventType eventType = StatusPageEventType.fromIncidentStatus(incident.getStatus(), true);
                return embedFactory.createIncidentEmbed(incident, eventType);
            });
    }

    private Stream<MessageEmbed> processUpdatedIncidents(StatusPageResponse oldData, StatusPageResponse newData) {
        return changeDetector.findUpdatedIncidents(oldData, newData).stream()
            .peek(incident -> log.info("Found updated incident: {} ({})", incident.getName(), incident.getStatus()))
            .map(incident -> {
                StatusPageEventType eventType = StatusPageEventType.fromIncidentStatus(incident.getStatus(), false);
                return embedFactory.createIncidentEmbed(incident, eventType);
            });
    }

    private Stream<MessageEmbed> processNewMaintenances(StatusPageResponse oldData, StatusPageResponse newData) {
        return changeDetector.findNewMaintenances(oldData, newData).stream()
            .peek(maintenance -> log.info("Found new maintenance: {} ({})", maintenance.getName(), maintenance.getStatus()))
            .map(maintenance -> {
                StatusPageEventType eventType = StatusPageEventType.fromMaintenanceStatus(maintenance.getStatus(), true);
                return embedFactory.createMaintenanceEmbed(maintenance, eventType);
            });
    }

    private Stream<MessageEmbed> processUpdatedMaintenances(StatusPageResponse oldData, StatusPageResponse newData) {
        return changeDetector.findUpdatedMaintenances(oldData, newData).stream()
            .peek(maintenance -> log.info("Found updated maintenance: {} ({})", maintenance.getName(), maintenance.getStatus()))
            .map(maintenance -> {
                StatusPageEventType eventType = StatusPageEventType.fromMaintenanceStatus(maintenance.getStatus(), false);
                return embedFactory.createMaintenanceEmbed(maintenance, eventType);
            });
    }

    private boolean hasIncidentChanges(StatusPageResponse oldData, StatusPageResponse newData) {
        return !changeDetector.findNewIncidents(oldData, newData).isEmpty() ||
            !changeDetector.findUpdatedIncidents(oldData, newData).isEmpty();
    }

    private boolean hasMaintenanceChanges(StatusPageResponse oldData, StatusPageResponse newData) {
        return !changeDetector.findNewMaintenances(oldData, newData).isEmpty() ||
            !changeDetector.findUpdatedMaintenances(oldData, newData).isEmpty();
    }

    private void sendNotification(List<MessageEmbed> embeds, boolean shouldPing, net.dv8tion.jda.api.entities.channel.concrete.TextChannel textChannel) {
        MessageCreateBuilder messageBuilder = new MessageCreateBuilder();

        if (shouldPing) {
            RoleManager.getPingableRoleByName(config.getStatusAlertRoleName()).ifPresent(pingableRole -> {
                messageBuilder.addContent(RoleManager.formatPingableRoleAsMention(pingableRole) + "\n\n");
            });
        }

        messageBuilder.setEmbeds(embeds);
        textChannel.sendMessage(messageBuilder.build()).queue();
    }
}
