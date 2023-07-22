package net.hypixel.nerdbot.api.database.model.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;

import java.util.List;

@AllArgsConstructor
@Getter
@Setter
public class DiscordUser {

    private String discordId;
    private List<String> agrees;
    private List<String> disagrees;
    private LastActivity lastActivity;
    private MojangProfile mojangProfile;

    public DiscordUser() {
    }

    public int getTotalMessageCount() {
        return lastActivity.getChannelActivity().values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean isProfileAssigned() {
        return this.mojangProfile != null && this.mojangProfile.getUniqueId() != null;
    }

    public boolean noProfileAssigned() {
        return !this.isProfileAssigned();
    }

}
