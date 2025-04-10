package net.hypixel.nerdbot.bot.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.internalapi.badge.Badge;

import java.util.List;

@Getter
@Setter
@ToString
public class BadgeConfig {

    /**
     * The list of badges that are available to be awarded
     */
    private List<Badge> badges;
}
