package com.jvibe.monitor;

import io.avaje.jsonb.Json;

@Json
public record MonitoredService(
    String id,
    String name,
    String serviceUrl,
    String host,
    String lastSeen,
    String health,
    MonitorSnapshot latestSnapshot) {
  public MonitoredService {
    id = blank(id) ? fallbackId(name, host, serviceUrl) : id;
    name = blank(name) ? id : name;
    serviceUrl = serviceUrl == null ? "" : serviceUrl;
    host = host == null ? "" : host;
    lastSeen = lastSeen == null ? "" : lastSeen;
    health = blank(health) ? "unknown" : health;
  }

  public static MonitoredService manual(String name, String serviceUrl, String host) {
    return new MonitoredService(fallbackId(name, host, serviceUrl), name, serviceUrl, host, "", "manual", null);
  }

  private static String fallbackId(String name, String host, String serviceUrl) {
    var source = first(name, host, serviceUrl, "service");
    return source.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.:-]+", "-");
  }

  private static String first(String... values) {
    for (var value : values) {
      if (!blank(value)) {
        return value;
      }
    }
    return "service";
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
