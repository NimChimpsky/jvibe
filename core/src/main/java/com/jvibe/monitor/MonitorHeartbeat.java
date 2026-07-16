package com.jvibe.monitor;

import io.avaje.jsonb.Json;

@Json
public record MonitorHeartbeat(
    String serviceId,
    String serviceName,
    String serviceUrl,
    String host,
    String health,
    MonitorSnapshot snapshot) {
}
