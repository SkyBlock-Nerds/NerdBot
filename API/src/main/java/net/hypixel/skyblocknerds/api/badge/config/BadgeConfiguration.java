package net.hypixel.skyblocknerds.api.badge.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.skyblocknerds.api.badge.Badge;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
public class BadgeConfiguration {

    /**
     * Toggle to enable or disable the badge system
     */
    private boolean enabled = true;

    /**
     * The list of {@link Badge badges} that are available
     */
    private List<Badge> badges = new ArrayList<>();
}
