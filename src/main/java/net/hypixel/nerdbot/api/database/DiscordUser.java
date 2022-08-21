package net.hypixel.nerdbot.api.database;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@AllArgsConstructor
@Getter
@Setter
public class DiscordUser {

    private String discordId;
    private Date lastKnownActivityDate;
    private List<String> agrees, disagrees;

    public DiscordUser() {
    }
}
