package com.jvibe.cli;

import com.jvibe.agent.AgentRunner;
import com.jvibe.session.AgentSession;
import com.jvibe.session.SessionFactory;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

final class GatewayServer {
  private final AgentRunner runner;
  private final SessionFactory sessionFactory;
  private final String host;
  private final int port;
  private final Path workspace;

  GatewayServer(AgentRunner runner, SessionFactory sessionFactory, String host, int port, Path workspace) {
    this.runner = runner;
    this.sessionFactory = sessionFactory;
    this.host = host;
    this.port = port;
    this.workspace = workspace;
  }

  HttpServer start() throws IOException {
    var server = HttpServer.create(new InetSocketAddress(host, port), 0);
    server.createContext("/health", exchange -> {
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
      var prompt = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
      exchange.sendResponseHeaders(200, 0);
      var session = session();
      try (var out = exchange.getResponseBody()) {
        runner.run(session, prompt).forEach(event -> {
          try {
            out.write((event.type() + " " + event.message() + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
      }
    });
    server.start();
    return server;
  }

  private AgentSession session() {
    return sessionFactory.create(workspace);
  }
}
