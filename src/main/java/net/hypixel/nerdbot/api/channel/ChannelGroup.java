package net.hypixel.nerdbot.api.channel;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class ChannelGroup {

    private String name, guildId, from, to;

    public ChannelGroup() {
    }
}
