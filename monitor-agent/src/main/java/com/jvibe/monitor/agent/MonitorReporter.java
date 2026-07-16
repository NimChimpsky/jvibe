package com.jvibe.monitor.agent;

import com.jvibe.monitor.MonitorHeartbeat;
import io.avaje.jsonb.Jsonb;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MonitorReporter implements AutoCloseable {
  private final MonitorConfig config;
  private final MonitorCollector collector;
  private final HttpClient http;
  private final Jsonb jsonb;
  private ScheduledExecutorService executor;

  public MonitorReporter(MonitorConfig config) {
    this(config, new MonitorCollector(config), HttpClient.newHttpClient(), Jsonb.instance());
  }

  MonitorReporter(MonitorConfig config, MonitorCollector collector, HttpClient http, Jsonb jsonb) {
    this.config = config;
    this.collector = collector;
    this.http = http;
    this.jsonb = jsonb;
  }

  public synchronized void start() {
    if (!config.enabled() || executor != null) {
      return;
    }
    executor = Executors.newSingleThreadScheduledExecutor(task -> {
      var thread = new Thread(task, "jvibe-monitor");
      thread.setDaemon(true);
      return thread;
    });
    executor.execute(this::sendRegistration);
    executor.scheduleWithFixedDelay(this::sendHeartbeat, 0, config.intervalSeconds(), TimeUnit.SECONDS);
  }

  @Override
  public synchronized void close() {
    if (executor != null) {
      executor.shutdownNow();
      executor = null;
    }
  }

  private void sendRegistration() {
    post("/monitor/register", jsonb.type(com.jvibe.monitor.MonitorRegistration.class).toJson(collector.registration()));
  }

  private void sendHeartbeat() {
    var heartbeat = new MonitorHeartbeat(
        config.serviceId(),
        config.serviceName(),
        config.serviceUrl(),
        MonitorConfig.hostName(),
        "ok",
        collector.snapshot());
    post("/monitor/heartbeat", jsonb.type(MonitorHeartbeat.class).toJson(heartbeat));
  }

  private void post(String path, String body) {
    try {
      var request = HttpRequest.newBuilder(endpoint(path))
          .timeout(Duration.ofSeconds(5))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();
      http.send(request, HttpResponse.BodyHandlers.discarding());
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    } catch (RuntimeException ignored) {
      // Monitoring should never bring down the host service.
    }
  }

  private URI endpoint(String path) {
    var base = config.orchestratorUri().toString();
    return URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path);
  }
}
