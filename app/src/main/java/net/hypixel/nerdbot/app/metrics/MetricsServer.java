package net.hypixel.nerdbot.app.metrics;

/** Starts and stops the Prometheus endpoint, behind an interface so commands can be tested without a port. */
public interface MetricsServer {

    void setEnabled(boolean enabled);
}
