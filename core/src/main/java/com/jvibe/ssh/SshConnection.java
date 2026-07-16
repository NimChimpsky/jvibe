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
    SshAccessMode accessMode,
    String lastTestResult) {
  public SshConnection {
    name = blank(name) ? host : name;
    host = host == null ? "" : host;
    user = user == null ? "" : user;
    port = port <= 0 ? 22 : port;
    keyPath = keyPath == null ? "" : keyPath;
    accessMode = accessMode == null ? SshAccessMode.READ_ONLY : accessMode;
    lastTestResult = lastTestResult == null ? "" : lastTestResult;
    id = blank(id) ? stableId(name, host, user, port) : id;
  }

  public SshConnection withLastTestResult(String value) {
    return new SshConnection(id, name, host, user, port, keyPath, accessMode, value);
  }

  public SshConnection withAccessMode(SshAccessMode value) {
    return new SshConnection(id, name, host, user, port, keyPath, value, lastTestResult);
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
