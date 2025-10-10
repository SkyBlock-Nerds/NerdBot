package net.hypixel.nerdbot.bot.config.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ForumAutoTag {

    /**
     * The forum channel ID where auto-tagging should be applied
     */
    private String forumChannelId;

    /**
     * The name of the tag to automatically apply to new posts
     * (e.g., "Submitted", "Pending Review")
     */
    private String defaultTagName;

    /**
     * The name of the tag that, when added, should remove the default tag
     * (e.g., "Reviewed", "Completed")
     */
    private String reviewTagName;
}
