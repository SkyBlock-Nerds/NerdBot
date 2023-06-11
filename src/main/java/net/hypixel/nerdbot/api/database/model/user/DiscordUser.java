package net.hypixel.nerdbot.api.database.model.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;

import java.util.List;

@AllArgsConstructor
@Getter
@Setter
public class DiscordUser {

    private String discordId;
    private List<String> agrees;
    private List<String> disagrees;
    private LastActivity lastActivity;

    public DiscordUser() {
    }
}
