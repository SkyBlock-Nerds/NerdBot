package net.hypixel.nerdbot.api.database.model.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.entities.Member;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;

import java.util.ArrayList;
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

    public DiscordUser(Member member) {
        this(member.getId(), new ArrayList<>(), new ArrayList<>(), new LastActivity(), new MojangProfile());
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
