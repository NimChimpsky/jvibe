package com.jvibe.monitor;

import io.avaje.jsonb.Json;

@Json
public record MonitorRegistration(
    String serviceId,
    String serviceName,
    String serviceUrl,
    String host,
    String javaVersion,
    String pid,
    String startedAt) {
}
