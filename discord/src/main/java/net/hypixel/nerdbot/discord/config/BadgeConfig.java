package net.hypixel.nerdbot.discord.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.discord.storage.badge.Badge;

@Getter
@Setter
@ToString
public class BadgeConfig {

    /**
     * The list of badges that are available to be awarded
     */
    private List<Badge> badges;
}
