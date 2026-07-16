package com.jvibe.monitor;

import io.avaje.jsonb.Json;

@Json
public record MonitorSnapshot(
    String capturedAt,
    String pid,
    String javaVersion,
    String host,
    JvmMetrics jvm) {
}
