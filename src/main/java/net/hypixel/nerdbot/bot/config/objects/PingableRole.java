package net.hypixel.nerdbot.bot.config.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PingableRole {

    private final String name;
    private final String roleId;

}
