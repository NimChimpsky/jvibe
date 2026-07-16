package com.jvibe.cli;

import com.jvibe.agent.AgentRunner;
import com.jvibe.session.AgentSession;
import com.jvibe.session.SessionFactory;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

final class GatewayServer implements AutoCloseable {
  private static final int MAX_PROMPT_BYTES = 1024 * 1024;
  private final AgentRunner runner;
  private final SessionFactory sessionFactory;
  private final String host;
  private final int port;
  private final Path workspace;
  private HttpServer server;
  private java.util.concurrent.ExecutorService executor;

  GatewayServer(AgentRunner runner, SessionFactory sessionFactory, String host, int port, Path workspace) {
    this.runner = runner;
    this.sessionFactory = sessionFactory;
    this.host = host;
    this.port = port;
    this.workspace = workspace;
  }

  synchronized GatewayServer start() throws IOException {
    if (server != null) {
      return this;
    }
    server = HttpServer.create(new InetSocketAddress(host, port), 0);
    executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(executor);
    server.createContext("/health", exchange -> {
      if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
        exchange.sendResponseHeaders(405, -1);
        exchange.close();
        return;
      }
      var response = "ok\n".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.createContext("/tasks", exchange -> {
      if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
        exchange.sendResponseHeaders(405, -1);
        exchange.close();
        return;
      }
      var body = exchange.getRequestBody().readNBytes(MAX_PROMPT_BYTES + 1);
      if (body.length > MAX_PROMPT_BYTES) {
        exchange.sendResponseHeaders(413, -1);
        exchange.close();
        return;
      }
      var prompt = new String(body, StandardCharsets.UTF_8).trim();
      if (prompt.isBlank()) {
        exchange.sendResponseHeaders(400, -1);
        exchange.close();
        return;
      }
      exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
      exchange.sendResponseHeaders(200, 0);
      var session = session();
      try (var out = exchange.getResponseBody()) {
        try {
          runner.run(session, prompt).forEach(event -> {
            try {
              out.write((event.type() + " " + event.message() + "\n").getBytes(StandardCharsets.UTF_8));
              out.flush();
            } catch (IOException e) {
              throw new ResponseWriteException(e);
            }
          });
        } catch (ResponseWriteException ignored) {
          // The caller disconnected while a response was streaming.
        }
      }
    });
    server.start();
    return this;
  }

  InetSocketAddress getAddress() {
    if (server == null) {
      throw new IllegalStateException("Gateway has not started");
    }
    return server.getAddress();
  }

  synchronized void stop(int delaySeconds) {
    if (server != null) {
      server.stop(delaySeconds);
      server = null;
    }
    if (executor != null) {
      executor.shutdownNow();
      executor = null;
    }
  }

  @Override
  public void close() {
    stop(0);
  }

  private AgentSession session() {
    return sessionFactory.create(workspace);
  }

  private static final class ResponseWriteException extends RuntimeException {
    private ResponseWriteException(IOException cause) {
      super(cause);
    }
  }
}
