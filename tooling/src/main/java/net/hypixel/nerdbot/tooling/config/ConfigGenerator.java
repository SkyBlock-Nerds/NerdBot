package net.hypixel.nerdbot.tooling.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.hypixel.nerdbot.core.api.badge.Badge;
import net.hypixel.nerdbot.core.api.badge.TieredBadge;
import net.hypixel.nerdbot.discord.config.channel.AlphaProjectConfig;
import net.hypixel.nerdbot.discord.config.BadgeConfig;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;
import net.hypixel.nerdbot.discord.config.EmojiConfig;
import net.hypixel.nerdbot.discord.config.MetricsConfig;
import net.hypixel.nerdbot.discord.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.config.RoleConfig;
import net.hypixel.nerdbot.discord.config.StatusPageConfig;
import net.hypixel.nerdbot.discord.config.channel.ChannelConfig;
import net.hypixel.nerdbot.discord.config.channel.ModMailConfig;
import net.hypixel.nerdbot.discord.config.objects.CustomForumTag;
import net.hypixel.nerdbot.discord.config.objects.PingableRole;
import net.hypixel.nerdbot.discord.config.objects.ReactionChannel;
import net.hypixel.nerdbot.discord.config.suggestion.ReviewRequestConfig;
import net.hypixel.nerdbot.discord.config.suggestion.SuggestionConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

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

        ModMailConfig modMailConfig = new ModMailConfig();
        modMailConfig.setChannelId(EXAMPLE_ID);
        modMailConfig.setRoleFormat(ModMailConfig.RoleFormat.BELOW);
        modMailConfig.setRoleId(EXAMPLE_ID);
        modMailConfig.setWebhookId(EXAMPLE_ID);
        modMailConfig.setTimeBetweenPings(60);
        botConfig.setModMailConfig(modMailConfig);

        RoleConfig roleConfig = new RoleConfig();
        roleConfig.setLimboRoleId(EXAMPLE_ID);
        roleConfig.setBotManagerRoleId(EXAMPLE_ID);
        roleConfig.setNewMemberRoleId(EXAMPLE_ID);
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
        File outputDir = new File("./tooling/src/main/resources");
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