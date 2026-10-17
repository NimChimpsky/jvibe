package com.jvibe.config;

import com.jvibe.jdbc.JdbcConnectionConfig;
import com.jvibe.ssh.SshConnection;
import com.jvibe.ssh.SshKeyRef;
import io.avaje.config.Config;
import io.avaje.jsonb.Json;
import io.avaje.jsonb.Jsonb;
import java.util.List;

@Json
public record JvibeConfig(
    int maxToolIterations,
    String theme,
    String customInstructions,
    List<String> mainTabOrder,
    List<SshConnection> sshConnections,
    List<SshKeyRef> sshKeys,
    List<JdbcConnectionConfig> jdbcConnections,
    List<String> projectPaths) {
  public JvibeConfig {
    maxToolIterations = maxToolIterations <= 0 ? 10 : maxToolIterations;
    theme = theme == null || theme.isBlank() ? "DRACULA" : theme;
    customInstructions = customInstructions == null ? "" : customInstructions;
    mainTabOrder = mainTabOrder == null || mainTabOrder.isEmpty()
        ? List.of("chat", "build", "servers", "databases", "jvibe") : List.copyOf(mainTabOrder);
    sshConnections = sshConnections == null ? List.of() : List.copyOf(sshConnections);
    sshKeys = sshKeys == null ? List.of() : List.copyOf(sshKeys);
    jdbcConnections = jdbcConnections == null ? List.of() : List.copyOf(jdbcConnections);
    projectPaths = projectPaths == null ? List.of() : List.copyOf(projectPaths);
  }

  public static JvibeConfig defaults() {
    return new JvibeConfig(Config.getInt("jvibe.maxToolIterations", 10),
        Config.get("jvibe.theme", "DRACULA"), "", null, null, null, null, null);
  }

  public JvibeConfig withTheme(String value) {
    return copy(maxToolIterations, value, customInstructions, mainTabOrder, sshConnections, sshKeys, jdbcConnections, projectPaths);
  }

  public JvibeConfig withMaxToolIterations(int value) {
    return copy(value, theme, customInstructions, mainTabOrder, sshConnections, sshKeys, jdbcConnections, projectPaths);
  }

  public JvibeConfig withCustomInstructions(String value) {
    return copy(maxToolIterations, theme, value, mainTabOrder, sshConnections, sshKeys, jdbcConnections, projectPaths);
  }

  public JvibeConfig withMainTabOrder(List<String> value) {
    return copy(maxToolIterations, theme, customInstructions, value, sshConnections, sshKeys, jdbcConnections, projectPaths);
  }

  public JvibeConfig withSshConfig(List<SshConnection> connections, List<SshKeyRef> keys) {
    return copy(maxToolIterations, theme, customInstructions, mainTabOrder, connections, keys, jdbcConnections, projectPaths);
  }

  public JvibeConfig withJdbcConnections(List<JdbcConnectionConfig> value) {
    return copy(maxToolIterations, theme, customInstructions, mainTabOrder, sshConnections, sshKeys, value, projectPaths);
  }

  public JvibeConfig withProjectPaths(List<String> value) {
    return copy(maxToolIterations, theme, customInstructions, mainTabOrder, sshConnections, sshKeys, jdbcConnections, value);
  }

  private static JvibeConfig copy(int iterations, String theme, String instructions,
      List<String> order, List<SshConnection> connections, List<SshKeyRef> keys,
      List<JdbcConnectionConfig> databases, List<String> projects) {
    return new JvibeConfig(iterations, theme, instructions, order, connections, keys, databases, projects);
  }

  public String toJson() { return Jsonb.instance().type(JvibeConfig.class).toJson(this); }
  public static JvibeConfig fromJson(String json) { return Jsonb.instance().type(JvibeConfig.class).fromJson(json); }
}
