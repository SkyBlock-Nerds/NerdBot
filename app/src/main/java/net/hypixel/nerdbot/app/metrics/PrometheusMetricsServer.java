package net.hypixel.nerdbot.app.metrics;

import jakarta.inject.Inject;

public class PrometheusMetricsServer implements MetricsServer {

    @Inject
    public PrometheusMetricsServer() {
    }

    @Override
    public void setEnabled(boolean enabled) {
        PrometheusMetrics.setMetricsEnabled(enabled);
    }
}
