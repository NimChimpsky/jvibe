package com.jvibe.monitor.agent;

import io.avaje.config.Config;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.UnknownHostException;

public record MonitorConfig(
    boolean enabled,
    String serviceId,
    String serviceName,
    URI orchestratorUri,
    String serviceUrl,
    int intervalSeconds) {
  public MonitorConfig {
    serviceId = blank(serviceId) ? defaultServiceId() : serviceId;
    serviceName = blank(serviceName) ? serviceId : serviceName;
    orchestratorUri = orchestratorUri == null ? URI.create("http://127.0.0.1:8738") : orchestratorUri;
    serviceUrl = serviceUrl == null ? "" : serviceUrl;
    intervalSeconds = intervalSeconds <= 0 ? 10 : intervalSeconds;
  }

  public static MonitorConfig load() {
    return new MonitorConfig(
        Config.getBool("jvibe.monitor.enabled", true),
        Config.get("jvibe.monitor.serviceId", defaultServiceId()),
        Config.get("jvibe.monitor.serviceName", defaultServiceId()),
        URI.create(Config.get("jvibe.monitor.orchestratorUrl", "http://127.0.0.1:8738")),
        Config.get("jvibe.monitor.serviceUrl", ""),
        Config.getInt("jvibe.monitor.intervalSeconds", 10));
  }

  static String defaultServiceId() {
    return hostName() + "-" + ManagementFactory.getRuntimeMXBean().getName().replace('@', '-');
  }

  static String hostName() {
    try {
      return java.net.InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return "localhost";
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
