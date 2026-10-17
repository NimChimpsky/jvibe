package com.jvibe.config;

import com.jvibe.api.Thinking;
import com.jvibe.jdbc.JdbcConnectionConfig;
import com.jvibe.session.Mode;
import com.jvibe.ssh.SshConnection;
import com.jvibe.ssh.SshKeyRef;
import io.avaje.jsonb.Json;
import io.avaje.jsonb.Jsonb;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** User configuration in {@code ~/.jvibe/config.json}. Unknown legacy fields are ignored on read. */
@Json
public record JvibeConfig(
    int maxToolIterations,
    String theme,
    String thinking,
    Map<String, ModeSettings> modes,
    List<String> mainTabOrder,
    List<SshConnection> sshConnections,
    List<SshKeyRef> sshKeys,
    List<JdbcConnectionConfig> jdbcConnections,
    List<String> projectPaths) {
  static final int DEFAULT_MAX_TOOL_ITERATIONS = 25;

  public JvibeConfig {
    maxToolIterations = maxToolIterations <= 0 ? DEFAULT_MAX_TOOL_ITERATIONS : maxToolIterations;
    theme = theme == null || theme.isBlank() ? "DRACULA" : theme;
    thinking = Thinking.fromId(thinking).id();
    modes = modes == null ? Map.of() : Map.copyOf(modes);
    mainTabOrder = mainTabOrder == null ? List.of() : List.copyOf(mainTabOrder);
    sshConnections = sshConnections == null ? List.of() : List.copyOf(sshConnections);
    sshKeys = sshKeys == null ? List.of() : List.copyOf(sshKeys);
    jdbcConnections = jdbcConnections == null ? List.of() : List.copyOf(jdbcConnections);
    projectPaths = projectPaths == null ? List.of() : List.copyOf(projectPaths);
  }

  public static JvibeConfig defaults() {
    return new JvibeConfig(0, null, null, null, null, null, null, null, null);
  }

  public ModeSettings mode(Mode mode) {
    return modes.getOrDefault(mode.id(), ModeSettings.DEFAULT);
  }

  public Thinking thinkingLevel() {
    return Thinking.fromId(thinking);
  }

  public JvibeConfig withTheme(String value) {
    return new JvibeConfig(maxToolIterations, value, thinking, modes, mainTabOrder, sshConnections, sshKeys, jdbcConnections, projectPaths);
  }

  public JvibeConfig withAssistant(Thinking value, int iterations, Map<Mode, ModeSettings> perMode) {
    var updated = new LinkedHashMap<>(modes);
    perMode.forEach((mode, settings) -> updated.put(mode.id(), settings));
    return new JvibeConfig(iterations, theme, value.id(), updated, mainTabOrder, sshConnections, sshKeys, jdbcConnections, projectPaths);
  }

  public JvibeConfig withMainTabOrder(List<String> value) {
    return new JvibeConfig(maxToolIterations, theme, thinking, modes, value, sshConnections, sshKeys, jdbcConnections, projectPaths);
  }

  public JvibeConfig withSshConfig(List<SshConnection> connections, List<SshKeyRef> keys) {
    return new JvibeConfig(maxToolIterations, theme, thinking, modes, mainTabOrder, connections, keys, jdbcConnections, projectPaths);
  }

  public JvibeConfig withJdbcConnections(List<JdbcConnectionConfig> value) {
    return new JvibeConfig(maxToolIterations, theme, thinking, modes, mainTabOrder, sshConnections, sshKeys, value, projectPaths);
  }

  public JvibeConfig withProjectPaths(List<String> value) {
    return new JvibeConfig(maxToolIterations, theme, thinking, modes, mainTabOrder, sshConnections, sshKeys, jdbcConnections, value);
  }

  public String toJson() { return Jsonb.instance().type(JvibeConfig.class).toJsonPretty(this); }
  public static JvibeConfig fromJson(String json) { return Jsonb.instance().type(JvibeConfig.class).fromJson(json); }
}
