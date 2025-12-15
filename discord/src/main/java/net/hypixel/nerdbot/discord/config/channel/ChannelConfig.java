package net.hypixel.nerdbot.discord.config.channel;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.discord.config.objects.CustomForumTag;
import net.hypixel.nerdbot.discord.config.objects.ForumAutoTag;
import net.hypixel.nerdbot.discord.config.objects.ReactionChannel;
import net.hypixel.nerdbot.discord.config.objects.RoleRestrictedChannelGroup;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
public class ChannelConfig {

    private String logChannelId = "";
    private String verifyLogChannelId = "";
    private String publicArchiveCategoryId = "";
    private String nerdArchiveCategoryId = "";
    private String alphaArchiveCategoryId = "";
    private String announcementChannelId = "";
    private String botSpamChannelId = "";
    private String pollChannelId = "";
    private String memberVotingChannelId = "";
    private String birthdayNotificationChannelId = "";

    private String[] genChannelIds = {};

    private String[] filteredAprilFoolsGenChannelIds = {};

    private List<ReactionChannel> reactionChannels = List.of();

    private String[] blacklistedChannels = {};

    private List<CustomForumTag> customForumTags = List.of();

    private List<RoleRestrictedChannelGroup> roleRestrictedChannelGroups = List.of();

    private boolean autoManageRoleRestrictedChannels = true;

    private int defaultMinimumMessagesForActivity = 5;
    private int defaultMinimumVotesForActivity = 2;
    private int defaultMinimumCommentsForActivity = 1;
    private int defaultActivityCheckDays = 30;

    private boolean autoPinFirstMessage = true;
    private String[] autoPinBlacklistedChannels = {};

    private String[] projectChannelNames = {};

    private List<ForumAutoTag> forumAutoTags = List.of();

    public void rebuildRoleRestrictedChannelGroups() {
        if (roleRestrictedChannelGroups == null) {
            roleRestrictedChannelGroups = List.of();
            return;
        }

        List<RoleRestrictedChannelGroup> rebuiltGroups = new ArrayList<>();
        for (RoleRestrictedChannelGroup group : roleRestrictedChannelGroups) {
            if (group == null) {
                continue;
            }

            String identifier = group.getIdentifier() == null ? "" : group.getIdentifier();
            String displayName = group.getDisplayName() == null || group.getDisplayName().isEmpty()
                ? identifier
                : group.getDisplayName();
            String[] channelIds = group.getChannelIds() == null ? new String[0] : group.getChannelIds();
            String[] requiredRoleIds = group.getRequiredRoleIds() == null ? new String[0] : group.getRequiredRoleIds();

            RoleRestrictedChannelGroup normalised = new RoleRestrictedChannelGroup(
                identifier,
                displayName,
                channelIds,
                requiredRoleIds,
                group.getMinimumMessagesForActivity(),
                group.getMinimumVotesForActivity(),
                group.getMinimumCommentsForActivity(),
                group.getActivityCheckDays(),
                group.isVotingNotificationsEnabled()
            );

            rebuiltGroups.add(normalised);
        }

        this.roleRestrictedChannelGroups = rebuiltGroups;
    }

    public boolean removeEmptyRoleRestrictedGroups() {
        if (roleRestrictedChannelGroups == null || roleRestrictedChannelGroups.isEmpty()) {
            return false;
        }

        List<RoleRestrictedChannelGroup> groups = new ArrayList<>(roleRestrictedChannelGroups);
        boolean removed = groups.removeIf(group -> group == null
            || group.getChannelIds() == null
            || group.getChannelIds().length == 0);

        if (removed) {
            this.roleRestrictedChannelGroups = groups;
        }

        return removed;
    }

    public ForumAutoTag getForumAutoTagConfig(String forumChannelId) {
        return forumAutoTags.stream()
            .filter(config -> config != null && config.getForumChannelId().equals(forumChannelId))
            .findFirst()
            .orElse(null);
    }

    public boolean addOrUpdateForumAutoTagConfig(String forumChannelId, String defaultTagName, String reviewTagName) {
        List<ForumAutoTag> autoTags = new ArrayList<>(forumAutoTags);

        for (int i = 0; i < autoTags.size(); i++) {
            ForumAutoTag tag = autoTags.get(i);
            if (tag != null && tag.getForumChannelId().equals(forumChannelId)) {
                autoTags.set(i, new ForumAutoTag(forumChannelId, defaultTagName, reviewTagName));
                this.forumAutoTags = autoTags;
                return false;
            }
        }

        autoTags.add(new ForumAutoTag(forumChannelId, defaultTagName, reviewTagName));
        this.forumAutoTags = autoTags;
        return true;
    }

    public boolean removeForumAutoTagConfig(String forumChannelId) {
        List<ForumAutoTag> configs = new ArrayList<>(forumAutoTags);
        boolean removed = configs.removeIf(config -> config != null && config.getForumChannelId().equals(forumChannelId));

        if (removed) {
            this.forumAutoTags = configs;
        }

        return removed;
    }
}