package net.hypixel.nerdbot.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class MetricsConfig {

    /**
     * Whether metrics should be enabled. This is disabled by default as it requires a Prometheus server to be running.
     */
    private boolean enabled = false;

    /**
     * The port that the HTTP server will be running on to serve metrics
     */
    private int port = 1234;

}