package net.hypixel.nerdbot.bot.config.generator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Activity;
import net.hypixel.nerdbot.api.badge.Badge;
import net.hypixel.nerdbot.api.badge.TieredBadge;
import net.hypixel.nerdbot.bot.config.BadgeConfig;
import net.hypixel.nerdbot.bot.config.BotConfig;
import net.hypixel.nerdbot.bot.config.EmojiConfig;
import net.hypixel.nerdbot.bot.config.MetricsConfig;
import net.hypixel.nerdbot.bot.config.RoleConfig;
import net.hypixel.nerdbot.bot.config.channel.AlphaProjectConfig;
import net.hypixel.nerdbot.bot.config.channel.ChannelConfig;
import net.hypixel.nerdbot.bot.config.channel.ModMailConfig;
import net.hypixel.nerdbot.bot.config.objects.CustomForumTag;
import net.hypixel.nerdbot.bot.config.objects.PingableRole;
import net.hypixel.nerdbot.bot.config.objects.ReactionChannel;
import net.hypixel.nerdbot.bot.config.suggestion.ReviewRequestConfig;
import net.hypixel.nerdbot.bot.config.suggestion.SuggestionConfig;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

@Log4j2
public class ConfigGenerator {

    private static final String EXAMPLE_NAME_ID = "example_name";
    private static final String EXAMPLE_NAME = "Example Name";

    private static final String EXAMPLE_ID = "1234567890123456789";

    public static void main(String[] args) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        BotConfig botConfig = new BotConfig();
        botConfig.setOwnerIds(List.of(EXAMPLE_ID));
        botConfig.setGuildId(EXAMPLE_ID);
        botConfig.setMessageLimit(100);
        botConfig.setMojangUsernameCacheTTL(12);
        botConfig.setVoiceThreshold(60);
        botConfig.setInterval(43_200_000);
        botConfig.setActivityType(Activity.ActivityType.WATCHING);
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
        channelConfig.setReactionChannels(List.of(new ReactionChannel(EXAMPLE_NAME_ID, EXAMPLE_ID, List.of(EXAMPLE_ID))));
        channelConfig.setCustomForumTags(List.of(new CustomForumTag(EXAMPLE_ID, EXAMPLE_NAME)));
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
        suggestionConfig.setAutoPinFirstMessage(true);
        suggestionConfig.setAutoArchiveThreshold(168);
        suggestionConfig.setAutoLockThreshold(168);
        botConfig.setSuggestionConfig(suggestionConfig);

        AlphaProjectConfig alphaProjectConfig = new AlphaProjectConfig();
        alphaProjectConfig.setAlphaForumIds(new String[]{EXAMPLE_ID});
        alphaProjectConfig.setProjectForumIds(new String[]{EXAMPLE_ID});
        alphaProjectConfig.setFlaredTag(EXAMPLE_NAME);
        alphaProjectConfig.setAutoCreateTags(true);
        alphaProjectConfig.setAutoPinFirstMessage(true);
        alphaProjectConfig.setAutoArchiveThreshold(168);
        alphaProjectConfig.setAutoLockThreshold(168);
        botConfig.setAlphaProjectConfig(alphaProjectConfig);

        String json = gson.toJson(botConfig);

        if (isValidJson(json)) {
            log.info("The provided JSON string is valid!");

            try {
                writeJsonToFile(json);
            } catch (IOException exception) {
                log.error("Error writing JSON to file", exception);
            }
        } else {
            log.error("The provided JSON string is invalid: \n" + json + "\n");
            System.exit(-1);
        }
    }

    private static boolean isValidJson(String jsonStr) {
        try {
            JsonParser.parseString(jsonStr);
            return true;
        } catch (JsonSyntaxException exception) {
            log.error("Invalid JSON string: " + jsonStr);
            return false;
        }
    }

    private static void writeJsonToFile(String json) throws IOException {
        try (FileWriter writer = new FileWriter("./src/main/resources/example-config.json")) {
            writer.write(json);
            log.info("Created JSON file successfully!");
        }
    }
}
