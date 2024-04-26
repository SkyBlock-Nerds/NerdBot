package net.hypixel.skyblocknerds.api.curator.configuration;

import lombok.Getter;
import lombok.Setter;
import net.hypixel.skyblocknerds.api.configuration.IConfiguration;

@Getter
@Setter
public class CuratorConfiguration implements IConfiguration {

    /**
     * Whether the {@link net.hypixel.skyblocknerds.api.curator.Curator} system is enabled
     */
    private boolean enabled = true;
}
