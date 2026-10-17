package com.jvibe.api;

import jakarta.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

@Singleton
public final class JdkHttpTransport implements HttpTransport {
  /** Synthetic status for transport failures, so callers handle them like HTTP errors. */
  static final int NETWORK_ERROR = 599;

  private final HttpClient client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(20))
      .build();

  @Override
  public HttpTextResponse get(URI uri, Map<String, String> headers) {
    return send(request(uri, headers, Duration.ofSeconds(60)).GET().build());
  }

  @Override
  public HttpTextResponse post(URI uri, Map<String, String> headers, String json) {
    return send(request(uri, headers, Duration.ofMinutes(1))
        .POST(HttpRequest.BodyPublishers.ofString(json)).build());
  }

  @Override
  public HttpStreamResponse postStream(URI uri, Map<String, String> headers, String json) {
    var request = request(uri, headers, Duration.ofMinutes(10))
        .POST(HttpRequest.BodyPublishers.ofString(json)).build();
    try {
      var response = client.send(request, HttpResponse.BodyHandlers.ofLines());
      return new HttpStreamResponse(response.statusCode(), response.body());
    } catch (IOException e) {
      return new HttpStreamResponse(NETWORK_ERROR, Stream.of(errorJson(e.getMessage())));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new HttpStreamResponse(NETWORK_ERROR, Stream.of(errorJson("request interrupted")));
    }
  }

  private static HttpRequest.Builder request(URI uri, Map<String, String> headers, Duration timeout) {
    var builder = HttpRequest.newBuilder(uri).timeout(timeout);
    headers.forEach(builder::header);
    return builder;
  }

  private HttpTextResponse send(HttpRequest request) {
    try {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return new HttpTextResponse(response.statusCode(), response.body());
    } catch (IOException e) {
      return new HttpTextResponse(NETWORK_ERROR, errorJson(e.getMessage()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new HttpTextResponse(NETWORK_ERROR, errorJson("request interrupted"));
    }
  }

  private static String errorJson(String message) {
    return JsonSupport.toJson(Map.of("error", Map.of("message",
        "Unable to reach JVibe: " + (message == null ? "network error" : message))));
  }
}
