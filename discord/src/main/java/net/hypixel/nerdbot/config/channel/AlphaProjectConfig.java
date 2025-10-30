package net.hypixel.nerdbot.config.channel;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class AlphaProjectConfig {

    private String[] alphaForumIds = {};

    private String[] projectForumIds = {};

    private boolean autoCreateTags = true;

    private int autoArchiveThreshold = 24 * 7;

    private int autoLockThreshold = -1;

}