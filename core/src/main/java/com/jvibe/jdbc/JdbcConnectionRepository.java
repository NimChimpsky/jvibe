package com.jvibe.jdbc;

import com.jvibe.config.ConfigService;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Singleton
public final class JdbcConnectionRepository {
  private final ConfigService configService;
  private final JdbcCredentialStore credentials;

  public JdbcConnectionRepository(ConfigService configService, JdbcCredentialStore credentials) {
    this.configService = configService;
    this.credentials = credentials;
  }

  public List<JdbcConnectionConfig> connections() {
    try {
      return configService.config().jdbcConnections().stream()
          .sorted(Comparator.comparing(JdbcConnectionConfig::name, String.CASE_INSENSITIVE_ORDER))
          .toList();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to load JDBC connections", e);
    }
  }

  public void save(JdbcConnectionConfig connection) {
    try {
      configService.updateConfig(existing -> {
        var connections = new ArrayList<>(existing.jdbcConnections());
        connections.removeIf(value -> value.id().equals(connection.id()));
        connections.add(connection);
        return existing.withJdbcConnections(connections);
      });
    } catch (IOException e) {
      throw new IllegalStateException("Unable to save JDBC connection", e);
    }
  }

  public void delete(String id) {
    try {
      configService.updateConfig(existing ->
          existing.withJdbcConnections(existing.jdbcConnections().stream()
              .filter(value -> !value.id().equals(id))
              .toList()));
      credentials.remove(id);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to delete JDBC connection", e);
    }
  }
}
