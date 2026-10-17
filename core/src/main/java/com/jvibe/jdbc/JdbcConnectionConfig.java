package com.jvibe.jdbc;

import io.avaje.jsonb.Json;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Json
public record JdbcConnectionConfig(
    String id,
    String name,
    String jdbcUrl,
    String username,
    String driverClass,
    String driverJar) {
  public JdbcConnectionConfig {
    jdbcUrl = jdbcUrl == null ? "" : jdbcUrl.trim();
    name = blank(name) ? jdbcUrl : name.trim();
    username = username == null ? "" : username.trim();
    driverClass = driverClass == null ? "" : driverClass.trim();
    driverJar = driverJar == null ? "" : driverJar.trim();
    id = blank(id) ? stableId(name, jdbcUrl, username) : id;
  }

  public static JdbcConnectionConfig create(
      String name,
      String jdbcUrl,
      String username,
      String driverClass,
      String driverJar) {
    return new JdbcConnectionConfig(
        UUID.randomUUID().toString(), name, jdbcUrl, username, driverClass, driverJar);
  }

  private static String stableId(String name, String url, String username) {
    var source = (name == null ? "" : name) + "\n" + (url == null ? "" : url) + "\n" + (username == null ? "" : username);
    return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
