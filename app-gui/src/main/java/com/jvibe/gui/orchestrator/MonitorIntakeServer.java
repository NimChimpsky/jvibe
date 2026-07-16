package com.jvibe.gui.orchestrator;

import com.jvibe.config.ConfigService;
import com.jvibe.monitor.MonitorHeartbeat;
import com.jvibe.monitor.MonitorRegistration;
import com.jvibe.monitor.MonitorRegistry;
import com.sun.net.httpserver.HttpServer;
import io.avaje.inject.External;
import io.avaje.jsonb.Jsonb;
import jakarta.inject.Singleton;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

@Singleton
public final class MonitorIntakeServer {
  private static final int MAX_REQUEST_BYTES = 1024 * 1024;
  private final ConfigService configService;
  private final MonitorRegistry registry;
  private final Jsonb jsonb = Jsonb.instance();
  private HttpServer server;
  private java.util.concurrent.ExecutorService executor;

  public MonitorIntakeServer(@External ConfigService configService, @External MonitorRegistry registry) {
    this.configService = configService;
    this.registry = registry;
  }

  public synchronized String startIfEnabled() throws IOException {
    var config = configService.config();
    if (!config.monitoringEnabled()) {
      return "Monitoring disabled";
    }
    if (server != null) {
      return endpoint();
    }
    server = HttpServer.create(new InetSocketAddress(config.monitoringHost(), config.monitoringPort()), 0);
    server.createContext("/health", exchange -> respond(exchange, 200, "ok\n"));
    server.createContext("/monitor/register", exchange -> handle(exchange, body ->
        registry.register(jsonb.type(MonitorRegistration.class).fromJson(body))));
    server.createContext("/monitor/heartbeat", exchange -> handle(exchange, body ->
        registry.heartbeat(jsonb.type(MonitorHeartbeat.class).fromJson(body))));
    executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(executor);
    server.start();
    return endpoint();
  }

  @PreDestroy
  public synchronized void stop() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
    if (executor != null) {
      executor.shutdownNow();
      executor = null;
    }
  }

  private String endpoint() throws IOException {
    var config = configService.config();
    return "http://" + config.monitoringHost() + ":" + config.monitoringPort();
  }

  private static void handle(
      com.sun.net.httpserver.HttpExchange exchange,
      java.util.function.Consumer<String> operation) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      respond(exchange, 405, "Method not allowed\n");
      return;
    }
    try {
      var bytes = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
      if (bytes.length > MAX_REQUEST_BYTES) {
        respond(exchange, 413, "Request body too large\n");
        return;
      }
      operation.accept(new String(bytes, StandardCharsets.UTF_8));
      respond(exchange, 200, "ok\n");
    } catch (RuntimeException e) {
      respond(exchange, 400, "Invalid monitor payload\n");
    }
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String text) throws IOException {
    var response = text.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
    exchange.sendResponseHeaders(status, response.length);
    try (var out = exchange.getResponseBody()) {
      out.write(response);
    }
  }
}
