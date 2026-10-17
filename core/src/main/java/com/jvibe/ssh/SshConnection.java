package com.jvibe.ssh;

import io.avaje.jsonb.Json;
import java.util.Locale;

@Json
public record SshConnection(
    String id,
    String name,
    String host,
    String user,
    int port,
    String keyPath,
    String lastTestResult) {
  public SshConnection {
    name = blank(name) ? host : name;
    host = host == null ? "" : host;
    user = user == null ? "" : user;
    port = port <= 0 ? 22 : port;
    keyPath = keyPath == null ? "" : keyPath;
    lastTestResult = lastTestResult == null ? "" : lastTestResult;
    id = blank(id) ? stableId(name, host, user, port) : id;
  }

  public SshConnection withLastTestResult(String value) {
    return new SshConnection(id, name, host, user, port, keyPath, value);
  }

  /** {@code user@host:port} for display and model context. */
  public String address() {
    return (user.isBlank() ? "" : user + "@") + host + (port == 22 ? "" : ":" + port);
  }

  static String stableId(String name, String host, String user, int port) {
    var source = first(name, user + "@" + host + ":" + port, host);
    return source.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.:-]+", "-");
  }

  private static String first(String... values) {
    for (var value : values) {
      if (!blank(value)) {
        return value;
      }
    }
    return "ssh";
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
