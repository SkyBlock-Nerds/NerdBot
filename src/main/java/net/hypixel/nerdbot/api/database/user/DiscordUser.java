package net.hypixel.nerdbot.api.database.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.api.database.user.stats.LastActivity;

import java.util.List;

@AllArgsConstructor
@Getter
@Setter
public class DiscordUser {

    private String discordId;
    private List<String> agrees, disagrees;
    private LastActivity lastActivity;

    public DiscordUser() {
    }
}
