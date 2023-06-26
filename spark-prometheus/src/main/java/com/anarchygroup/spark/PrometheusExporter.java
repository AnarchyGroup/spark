package com.anarchygroup.spark;

import io.prometheus.client.exporter.HTTPServer;
import me.lucko.spark.common.SparkPlatform;

import java.io.IOException;
import java.util.logging.Level;

public final class PrometheusExporter {

    private final transient SparkPlatform platform;

    private HTTPServer httpServer;

    public PrometheusExporter(SparkPlatform platform) {
        this.platform = platform;
    }

    public void start() {
        try {
            this.httpServer = new HTTPServer.Builder()
                    .withPort(25575)
                    .build();

        } catch (IOException e) {
            this.platform.getPlugin().log(Level.SEVERE, "Unable to start Prometheus exporter");
            e.printStackTrace();
        }
    }

}
