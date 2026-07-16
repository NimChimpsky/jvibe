package com.jvibe.monitor;

import com.jvibe.config.ConfigService;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

@Singleton
public final class MonitorRegistry {
  private final ConfigService configService;
  private final LinkedHashMap<String, MonitoredService> services = new LinkedHashMap<>();
  private boolean loaded;

  public MonitorRegistry(ConfigService configService) {
    this.configService = configService;
  }

  public synchronized List<MonitoredService> services() {
    load();
    return List.copyOf(services.values());
  }

  public synchronized MonitoredService upsertManual(String name, String serviceUrl, String host) {
    load();
    return save(MonitoredService.manual(name, serviceUrl, host));
  }

  public synchronized void remove(String id) {
    load();
    services.remove(id);
    persist();
  }

  public synchronized MonitoredService register(MonitorRegistration registration) {
    load();
    var service = new MonitoredService(
        registration.serviceId(),
        registration.serviceName(),
        registration.serviceUrl(),
        registration.host(),
        registration.startedAt(),
        "registered",
        null);
    return save(service);
  }

  public synchronized MonitoredService heartbeat(MonitorHeartbeat heartbeat) {
    load();
    var snapshot = heartbeat.snapshot();
    var service = new MonitoredService(
        heartbeat.serviceId(),
        heartbeat.serviceName(),
        heartbeat.serviceUrl(),
        heartbeat.host(),
        snapshot == null ? Instant.now().toString() : snapshot.capturedAt(),
        heartbeat.health(),
        snapshot);
    return save(service);
  }

  private MonitoredService save(MonitoredService service) {
    services.put(service.id(), service);
    persist();
    return service;
  }

  private void load() {
    if (loaded) {
      return;
    }
    try {
      configService.config().monitoredServices().forEach(service -> services.put(service.id(), service));
    } catch (IOException ignored) {
      // Keep an empty in-memory registry if config is temporarily unavailable.
    }
    loaded = true;
  }

  private void persist() {
    try {
      configService.updateConfig(existing -> existing.withMonitoredServices(List.copyOf(services.values())));
    } catch (IOException ignored) {
      // GUI callers keep the latest state in memory and can retry on the next update.
    }
  }
}
