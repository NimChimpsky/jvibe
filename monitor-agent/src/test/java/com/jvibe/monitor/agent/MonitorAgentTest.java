package com.jvibe.monitor.agent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MonitorAgentTest {
  @AfterEach
  void stopMonitor() {
    JvibeMonitor.stop();
  }

  @Test
  void collectorBuildsJvmSnapshot() {
    var config = new MonitorConfig(false, "svc", "Service", URI.create("http://127.0.0.1:1"), "", 1);
    var snapshot = new MonitorCollector(config).snapshot();

    assertNotNull(snapshot.jvm());
    assertTrue(snapshot.jvm().threadCount() > 0);
    assertTrue(snapshot.jvm().heapCommittedBytes() >= 0);
  }

  @Test
  void monitorStartIsIdempotent() {
    var config = new MonitorConfig(false, "svc", "Service", URI.create("http://127.0.0.1:1"), "", 1);

    assertTrue(JvibeMonitor.start(config));
    assertTrue(!JvibeMonitor.start(config));
  }

  @Test
  void reporterPostsRegistrationAndHeartbeat() throws Exception {
    var latch = new CountDownLatch(2);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/monitor/register", exchange -> {
      exchange.getRequestBody().readAllBytes();
      respond(exchange);
      latch.countDown();
    });
    server.createContext("/monitor/heartbeat", exchange -> {
      var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(body.contains("\"serviceId\":\"svc\""), body);
      respond(exchange);
      latch.countDown();
    });
    server.start();
    try {
      var config = new MonitorConfig(
          true,
          "svc",
          "Service",
          URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
          "http://127.0.0.1:8080",
          1);
      var reporter = new MonitorReporter(config);
      reporter.start();
      assertTrue(latch.await(3, TimeUnit.SECONDS));
      reporter.close();
    } finally {
      server.stop(0);
    }
  }

  @Test
  void javaAgentEntryPointStartsWithoutThrowing() {
    JvibeMonitorAgent.premain("", null);
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
    var response = "ok\n".getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(200, response.length);
    try (var out = exchange.getResponseBody()) {
      out.write(response);
    }
  }
}
