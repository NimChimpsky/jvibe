package com.jvibe.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GatewayServerTest {
  @Test
  void healthEndpointReturnsOk() throws Exception {
    var server = new GatewayServer(null, null, "127.0.0.1", 0, Path.of(".")).start();
    try {
      var port = server.getAddress().getPort();
      var response = HttpClient.newHttpClient().send(
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health")).GET().build(),
          HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertEquals("ok\n", response.body());
    } finally {
      server.stop(0);
    }
  }
}
