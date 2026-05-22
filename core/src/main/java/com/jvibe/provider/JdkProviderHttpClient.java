package com.jvibe.provider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import jakarta.inject.Singleton;

@Singleton
public final class JdkProviderHttpClient implements ProviderHttpClient {
  private final HttpClient client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(20))
      .build();

  @Override
  public HttpTextResponse get(URI uri, Map<String, String> headers) {
    var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET();
    headers.forEach(builder::header);
    return send(builder.build());
  }

  @Override
  public HttpTextResponse post(URI uri, Map<String, String> headers, String json) {
    var builder = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofMinutes(5))
        .POST(HttpRequest.BodyPublishers.ofString(json));
    headers.forEach(builder::header);
    return send(builder.build());
  }

  @Override
  public HttpStreamResponse postStream(URI uri, Map<String, String> headers, String json) {
    var builder = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofMinutes(10))
        .POST(HttpRequest.BodyPublishers.ofString(json));
    headers.forEach(builder::header);
    try {
      var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofLines());
      return new HttpStreamResponse(response.statusCode(), response.body());
    } catch (IOException e) {
      return new HttpStreamResponse(599, java.util.stream.Stream.of("{\"error\":{\"message\":\"" + escape(e.getMessage()) + "\"}}"));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new HttpStreamResponse(599, java.util.stream.Stream.of("{\"error\":{\"message\":\"request interrupted\"}}"));
    }
  }

  private HttpTextResponse send(HttpRequest request) {
    try {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return new HttpTextResponse(response.statusCode(), response.body());
    } catch (IOException e) {
      return new HttpTextResponse(599, e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new HttpTextResponse(599, "request interrupted");
    }
  }

  private static String escape(String value) {
    return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
