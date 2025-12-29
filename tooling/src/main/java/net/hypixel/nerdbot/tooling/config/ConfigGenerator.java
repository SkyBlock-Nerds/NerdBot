package net.hypixel.nerdbot.tooling.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.hypixel.nerdbot.discord.config.BadgeConfig;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;
import net.hypixel.nerdbot.discord.config.EmojiConfig;
import net.hypixel.nerdbot.discord.config.MetricsConfig;
import net.hypixel.nerdbot.discord.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.config.FeatureConfig;
import net.hypixel.nerdbot.discord.config.WatcherConfig;
import net.hypixel.nerdbot.discord.config.RoleConfig;
import net.hypixel.nerdbot.discord.config.StatusPageConfig;
import net.hypixel.nerdbot.discord.config.channel.AlphaProjectConfig;
import net.hypixel.nerdbot.discord.config.channel.ChannelConfig;
import net.hypixel.nerdbot.discord.config.channel.TicketConfig;
import net.hypixel.nerdbot.discord.config.channel.TicketStatusConfig;
import net.hypixel.nerdbot.discord.config.channel.TicketReminderThreshold;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.TicketStatus;
import net.hypixel.nerdbot.discord.config.channel.TicketTemplate;
import net.hypixel.nerdbot.discord.config.channel.TicketTemplateField;
import net.hypixel.nerdbot.discord.config.objects.CustomForumTag;
import net.hypixel.nerdbot.discord.config.objects.PingableRole;
import net.hypixel.nerdbot.discord.config.objects.ReactionChannel;
import net.hypixel.nerdbot.discord.config.suggestion.ReviewRequestConfig;
import net.hypixel.nerdbot.discord.config.suggestion.SuggestionConfig;
import net.hypixel.nerdbot.discord.storage.badge.Badge;
import net.hypixel.nerdbot.discord.storage.badge.TieredBadge;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class ConfigGenerator {

    private static final String EXAMPLE_NAME_ID = "example_name";
    private static final String EXAMPLE_NAME = "Example Name";

    private static final String EXAMPLE_ID = "1234567890123456789";

    public static void main(String[] args) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        NerdBotConfig botConfig = new NerdBotConfig();
        botConfig.setOwnerIds(List.of(EXAMPLE_ID));
        botConfig.setGuildId(EXAMPLE_ID);
        botConfig.setMessageLimit(100);
        botConfig.setMojangUsernameCacheTTL(12);
        botConfig.setVoiceThreshold(60);
        botConfig.setInterval(43_200_000);
        botConfig.setActivityType(DiscordBotConfig.ActivityType.WATCHING);
        botConfig.setActivity("with an example message!");
        botConfig.setInactivityDays(7);

        BadgeConfig badgeConfig = new BadgeConfig();
        badgeConfig.setBadges(List.of(
            new Badge(EXAMPLE_ID, EXAMPLE_NAME, EXAMPLE_ID),
            new TieredBadge(EXAMPLE_NAME_ID, EXAMPLE_NAME, List.of(
                new TieredBadge.Tier(EXAMPLE_NAME, EXAMPLE_ID, 1),
                new TieredBadge.Tier(EXAMPLE_NAME, EXAMPLE_ID, 2),
                new TieredBadge.Tier(EXAMPLE_NAME, EXAMPLE_ID, 3)
            ))
        ));
        botConfig.setBadgeConfig(badgeConfig);

        ChannelConfig channelConfig = new ChannelConfig();
        channelConfig.setBlacklistedChannels(new String[]{EXAMPLE_ID});
        channelConfig.setAnnouncementChannelId(EXAMPLE_ID);
        channelConfig.setLogChannelId(EXAMPLE_ID);
        channelConfig.setGenChannelIds(new String[]{EXAMPLE_ID});
        channelConfig.setPollChannelId(EXAMPLE_ID);
        channelConfig.setBotSpamChannelId(EXAMPLE_ID);
        channelConfig.setVerifyLogChannelId(EXAMPLE_ID);
        channelConfig.setPublicArchiveCategoryId(EXAMPLE_ID);
        channelConfig.setAlphaArchiveCategoryId(EXAMPLE_ID);
        channelConfig.setNerdArchiveCategoryId(EXAMPLE_ID);
        channelConfig.setReactionChannels(List.of(new ReactionChannel(EXAMPLE_NAME_ID, EXAMPLE_ID, List.of(EXAMPLE_ID), true)));
        channelConfig.setCustomForumTags(List.of(new CustomForumTag(EXAMPLE_ID, EXAMPLE_NAME)));
        channelConfig.setAutoPinFirstMessage(true);
        channelConfig.setAutoPinBlacklistedChannels(new String[]{EXAMPLE_ID});
        botConfig.setChannelConfig(channelConfig);

        EmojiConfig emojiConfig = new EmojiConfig();
        emojiConfig.setAgreeEmojiId(EXAMPLE_ID);
        emojiConfig.setDisagreeEmojiId(EXAMPLE_ID);
        emojiConfig.setNeutralEmojiId(EXAMPLE_ID);
        emojiConfig.setGreenlitEmojiId(EXAMPLE_ID);
        botConfig.setEmojiConfig(emojiConfig);

        MetricsConfig metricsConfig = new MetricsConfig();
        metricsConfig.setEnabled(true);
        metricsConfig.setPort(1234);
        botConfig.setMetricsConfig(metricsConfig);

        TicketConfig ticketConfig = new TicketConfig();
        ticketConfig.setTicketCategoryId(EXAMPLE_ID);
        ticketConfig.setClosedTicketCategoryId(EXAMPLE_ID);
        ticketConfig.setTicketRoleId(EXAMPLE_ID);
        ticketConfig.setWebhookId(EXAMPLE_ID);
        ticketConfig.setCategories(List.of(
            new TicketConfig.TicketCategory("example_general", "Example General", "Example description for a general ticket."),
            new TicketConfig.TicketCategory("example_bug_report", "Example Bug Report", "Example description for bug reports."),
            new TicketConfig.TicketCategory("example_request", "Example Request", "Example description for special requests.")
        ));
        ticketConfig.setStatuses(List.of(
            new TicketStatusConfig("open", "Open", "\uD83D\uDFE2"),
            new TicketStatusConfig("in_progress", "In Progress", "\uD83D\uDFE1"),
            new TicketStatusConfig("awaiting_response", "Awaiting Response", "\uD83D\uDFE0"),
            new TicketStatusConfig("closed", "Closed", "\u26AB")
        ));
        ticketConfig.setUserReplyStatus(TicketStatus.OPEN);
        ticketConfig.setStaffReplyStatus(TicketStatus.AWAITING_RESPONSE);
        ticketConfig.setRemindersEnabled(true);
        ticketConfig.setReminderThresholds(List.of(
            new TicketReminderThreshold(4, "Example reminder after 4 hours without a response.", true),
            new TicketReminderThreshold(24, "Example reminder after 24 hours without a response.", true),
            new TicketReminderThreshold(72, "Example reminder after 72 hours without a response.", true)
        ));
        ticketConfig.setReminderCheckIntervalMinutes(30);
        ticketConfig.setUseModalFlow(true);

        TicketTemplate exampleBugTemplate = new TicketTemplate();
        exampleBugTemplate.setCategoryId("example_bug_report");
        exampleBugTemplate.setModalTitle("Example Ticket Form");
        exampleBugTemplate.setFields(List.of(
            new TicketTemplateField("example_subject", "Example Subject", "Example subject placeholder.", true, "SHORT", 2, 100),
            new TicketTemplateField("example_description", "Example Description", "Example description placeholder.", true, "PARAGRAPH", 20, 2000),
            new TicketTemplateField("example_optional", "Example Optional Field", "Optional example placeholder.", false, "PARAGRAPH", 1, 1000)
        ));

        TicketTemplate exampleRequestTemplate = new TicketTemplate();
        exampleRequestTemplate.setCategoryId("example_request");
        exampleRequestTemplate.setModalTitle("Example Request Form");
        exampleRequestTemplate.setFields(List.of(
            new TicketTemplateField("example_duration", "Example Duration", "Example duration placeholder.", true, "SHORT", 2, 100),
            new TicketTemplateField("example_reason", "Example Reason", "Example optional reason placeholder.", false, "PARAGRAPH", 1, 500)
        ));

        ticketConfig.setTemplates(List.of(exampleBugTemplate, exampleRequestTemplate));
        ticketConfig.setMaxOpenTicketsPerUser(3);
        ticketConfig.setTimeBetweenPings(60);
        ticketConfig.setStoreTranscripts(true);
        ticketConfig.setUploadTranscriptOnClose(true);
        ticketConfig.setAutoCloseEnabled(true);
        ticketConfig.setAutoCloseDays(7);
        ticketConfig.setAutoCloseStatus(TicketStatus.AWAITING_RESPONSE);
        ticketConfig.setAutoCloseMessage("Example auto-close message explaining why a ticket was closed.");
        botConfig.setTicketConfig(ticketConfig);

        RoleConfig roleConfig = new RoleConfig();
        roleConfig.setLimboRoleId(EXAMPLE_ID);
        roleConfig.setBotManagerRoleId(EXAMPLE_ID);
        roleConfig.setNewMemberRoleId(EXAMPLE_ID);
        roleConfig.setPromotionTierRoleIds(new String[]{EXAMPLE_ID, EXAMPLE_ID, EXAMPLE_ID});
        roleConfig.setPingableRoles(new PingableRole[]{new PingableRole(EXAMPLE_NAME, EXAMPLE_ID)});
        botConfig.setRoleConfig(roleConfig);

        SuggestionConfig suggestionConfig = new SuggestionConfig();
        ReviewRequestConfig reviewRequestConfig = new ReviewRequestConfig();
        reviewRequestConfig.setChannelId(EXAMPLE_ID);
        reviewRequestConfig.setThreshold(15);
        reviewRequestConfig.setEnforceGreenlitRatio(false);
        reviewRequestConfig.setMinimumSuggestionAge(604_800_000);
        suggestionConfig.setReviewRequestConfig(reviewRequestConfig);

        suggestionConfig.setForumChannelId(EXAMPLE_ID);
        suggestionConfig.setGreenlitTag(EXAMPLE_NAME);
        suggestionConfig.setReviewedTag(EXAMPLE_NAME);
        suggestionConfig.setGreenlitThreshold(20);
        suggestionConfig.setGreenlitRatio(75);
        suggestionConfig.setArchiveOnGreenlit(false);
        suggestionConfig.setLockOnGreenlit(false);
        suggestionConfig.setAutoArchiveThreshold(168);
        suggestionConfig.setAutoLockThreshold(168);
        botConfig.setSuggestionConfig(suggestionConfig);

        AlphaProjectConfig alphaProjectConfig = new AlphaProjectConfig();
        alphaProjectConfig.setAlphaForumIds(new String[]{EXAMPLE_ID});
        alphaProjectConfig.setProjectForumIds(new String[]{EXAMPLE_ID});
        alphaProjectConfig.setAutoCreateTags(true);
        alphaProjectConfig.setAutoArchiveThreshold(168);
        alphaProjectConfig.setAutoLockThreshold(168);
        botConfig.setAlphaProjectConfig(alphaProjectConfig);

        StatusPageConfig statusPageConfig = new StatusPageConfig();
        statusPageConfig.setOperationalColor("00C851");
        statusPageConfig.setDegradedColor("FFBB33");
        statusPageConfig.setPartialOutageColor("FF4444");
        statusPageConfig.setMajorOutageColor("8B0000");
        statusPageConfig.setMaintenanceColor("3498DB");
        statusPageConfig.setMaxDescriptionLength(200);
        statusPageConfig.setIncludeResolvedIncidents(true);
        statusPageConfig.setIncludeCompletedMaintenances(true);
        statusPageConfig.setEnableStatusAlerts(true);
        statusPageConfig.setStatusAlertRoleName("Status Alerts");
        statusPageConfig.setEnableMaintenanceAlerts(false);
        botConfig.setStatusPageConfig(statusPageConfig);

        List<FeatureConfig> featureConfigs = new ArrayList<>();
        FeatureConfig hello = new FeatureConfig();
        hello.setClassName("com.example.bot.feature.HelloGoodbyeFeature");
        hello.setEnabled(true);
        featureConfigs.add(hello);

        FeatureConfig curate = new FeatureConfig();
        curate.setClassName("com.example.bot.feature.CurateFeature");
        curate.setEnabled(true);
        curate.setInitialDelayMs(30000L);
        curate.setPeriodMs(43_200_000L); // 12 hours
        featureConfigs.add(curate);

        botConfig.setFeatures(featureConfigs);

        List<WatcherConfig> watcherConfigs = new ArrayList<>();

        WatcherConfig statusWatcher = new WatcherConfig();
        statusWatcher.setClassName("com.example.bot.urlwatcher.JsonURLWatcher");
        statusWatcher.setUrl("https://status.example.com/api/v2/summary.json");
        statusWatcher.setHandlerClass("com.example.bot.urlwatcher.handler.StatusPageDataHandler");
        statusWatcher.setInterval(1);
        statusWatcher.setTimeUnit(TimeUnit.MINUTES);
        statusWatcher.setEnabled(true);
        watcherConfigs.add(statusWatcher);

        WatcherConfig fireSaleWatcher = new WatcherConfig();
        fireSaleWatcher.setClassName("com.example.bot.urlwatcher.JsonURLWatcher");
        fireSaleWatcher.setUrl("https://api.example.com/firesales");
        fireSaleWatcher.setHandlerClass("com.example.bot.urlwatcher.handler.FireSaleDataHandler");
        fireSaleWatcher.setInterval(1);
        fireSaleWatcher.setTimeUnit(TimeUnit.MINUTES);
        watcherConfigs.add(fireSaleWatcher);

        WatcherConfig rssWatcher = new WatcherConfig();
        rssWatcher.setClassName("com.example.bot.urlwatcher.ThreadURLWatcher");
        rssWatcher.setUrl("https://forum.example.com/patch-notes.rss");
        rssWatcher.setInterval(1);
        rssWatcher.setTimeUnit(TimeUnit.MINUTES);
        watcherConfigs.add(rssWatcher);

        botConfig.setWatchers(watcherConfigs);

        String json = gson.toJson(botConfig);

        if (isValidJson(json)) {
            System.out.println("The provided JSON string is valid!");

            try {
                writeJsonToFile(json);
            } catch (IOException exception) {
                System.err.println("Error writing JSON to file");
                exception.printStackTrace();
            }
        } else {
            System.err.println("The provided JSON string is invalid: \n" + json + "\n");
            System.exit(-1);
        }
    }

    private static boolean isValidJson(String jsonStr) {
        try {
            JsonParser.parseString(jsonStr);
            return true;
        } catch (JsonSyntaxException exception) {
            System.err.println("Invalid JSON string: " + jsonStr);
            return false;
        }
    }

    private static void writeJsonToFile(String json) throws IOException {
        File outputDir = new File(".");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Failed to create output directory " + outputDir.getAbsolutePath());
        }

        File outputFile = new File(outputDir, "example-config.json");

        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(json);
            System.out.println("Created JSON file successfully!");
        }
    }
}