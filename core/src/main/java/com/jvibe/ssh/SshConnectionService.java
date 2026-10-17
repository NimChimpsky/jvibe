package com.jvibe.ssh;

import com.jvibe.config.ConfigService;
import com.jvibe.config.JvibeConfig;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@Singleton
public final class SshConnectionService {
  private final ConfigService configService;
  private final SshImportService importService;

  public SshConnectionService(ConfigService configService, SshImportService importService) {
    this.configService = configService;
    this.importService = importService;
  }

  public List<SshConnection> connections() {
    return config().sshConnections();
  }

  public List<SshKeyRef> keys() {
    return config().sshKeys();
  }

  public Optional<SshConnection> find(String id) {
    return connections().stream().filter(connection -> connection.id().equals(id)).findFirst();
  }

  public SshConnection save(SshConnection connection) {
    var merged = new LinkedHashMap<String, SshConnection>();
    connections().forEach(existing -> merged.put(existing.id(), existing));
    merged.put(connection.id(), connection);
    saveConfig(List.copyOf(merged.values()), keys());
    return connection;
  }

  public void delete(String id) {
    saveConfig(connections().stream().filter(connection -> !connection.id().equals(id)).toList(), keys());
  }

  public void saveTestResult(String id, String result) {
    saveConfig(connections().stream()
        .map(connection -> connection.id().equals(id) ? connection.withLastTestResult(result) : connection)
        .toList(), keys());
  }

  public SshConnectionConfig importAll() {
    var imported = importService.importAll();
    var connections = new LinkedHashMap<String, SshConnection>();
    connections().forEach(connection -> connections.put(connection.id(), connection));
    imported.connections().forEach(connection -> connections.putIfAbsent(connection.id(), connection));
    var keys = new LinkedHashMap<String, SshKeyRef>();
    keys().forEach(key -> keys.put(key.id(), key));
    imported.keys().forEach(key -> keys.putIfAbsent(key.id(), key));
    saveConfig(List.copyOf(connections.values()), List.copyOf(keys.values()));
    return new SshConnectionConfig(List.copyOf(connections.values()), List.copyOf(keys.values()));
  }

  private JvibeConfig config() {
    try {
      return configService.config();
    } catch (IOException e) {
      return JvibeConfig.defaults();
    }
  }

  private void saveConfig(List<SshConnection> connections, List<SshKeyRef> keys) {
    try {
      configService.updateConfig(existing -> existing.withSshConfig(connections, keys));
    } catch (IOException ignored) {
      // GUI callers can retry on the next user action.
    }
  }
}
