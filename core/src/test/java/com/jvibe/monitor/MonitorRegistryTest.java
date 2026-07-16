package com.jvibe.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jvibe.config.ConfigService;
import com.jvibe.config.JvibePaths;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MonitorRegistryTest {
  @TempDir
  Path home;

  @Test
  void registrationAndHeartbeatPersistServices() throws Exception {
    var config = new ConfigService(JvibePaths.under(home));
    var registry = new MonitorRegistry(config);
    registry.register(new MonitorRegistration("svc", "Service", "http://service", "10.0.0.3", "21", "123", "start"));
    registry.heartbeat(new MonitorHeartbeat(
        "svc",
        "Service",
        "http://service",
        "10.0.0.3",
        "ok",
        new MonitorSnapshot("now", "123", "21", "host", new JvmMetrics(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12))));

    var saved = config.config().monitoredServices().getFirst();
    assertEquals("svc", saved.id());
    assertEquals("ok", saved.health());
    assertEquals(7, saved.latestSnapshot().jvm().threadCount());
  }
}
