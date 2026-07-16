package com.jvibe.config;

import com.jvibe.deployment.DeploymentProfile;
import com.jvibe.monitor.MonitoredService;
import com.jvibe.ssh.SshConnection;
import com.jvibe.ssh.SshKeyRef;
import io.avaje.config.Config;
import io.avaje.jsonb.Json;
import java.util.List;

@Json
public record JvibeConfig(
    String defaultProvider,
    String defaultModel,
    int maxToolIterations,
    String theme,
    boolean gatewayEnabled,
    String gatewayHost,
    int gatewayPort,
    List<DeploymentProfile> deploymentProfiles,
    List<String> mainTabOrder,
    boolean monitoringEnabled,
    String monitoringHost,
    int monitoringPort,
    List<MonitoredService> monitoredServices,
    List<SshConnection> sshConnections,
    List<SshKeyRef> sshKeys) {
  public JvibeConfig {
    defaultProvider = blank(defaultProvider) ? "jvibe" : defaultProvider;
    defaultModel = blank(defaultModel) ? "jvibe-coder" : defaultModel;
    maxToolIterations = maxToolIterations <= 0 ? 10 : maxToolIterations;
    theme = blank(theme) ? "DRACULA" : theme;
    gatewayHost = blank(gatewayHost) ? "127.0.0.1" : gatewayHost;
    gatewayPort = gatewayPort <= 0 ? 8737 : gatewayPort;
    deploymentProfiles = deploymentProfiles == null ? List.of() : List.copyOf(deploymentProfiles);
    mainTabOrder = mainTabOrder == null || mainTabOrder.isEmpty()
        ? defaultMainTabOrder()
        : List.copyOf(mainTabOrder);
    monitoringHost = blank(monitoringHost) ? "0.0.0.0" : monitoringHost;
    monitoringPort = monitoringPort <= 0 ? 8738 : monitoringPort;
    monitoredServices = monitoredServices == null ? List.of() : List.copyOf(monitoredServices);
    sshConnections = sshConnections == null ? List.of() : List.copyOf(sshConnections);
    sshKeys = sshKeys == null ? List.of() : List.copyOf(sshKeys);
  }

  public static JvibeConfig defaults() {
    return new JvibeConfig(
        Config.get("jvibe.defaultProvider", "jvibe"),
        Config.get("jvibe.defaultModel", "jvibe-coder"),
        Config.getInt("jvibe.maxToolIterations", 10),
        Config.get("jvibe.theme", "DRACULA"),
        Config.getBool("jvibe.gatewayEnabled", false),
        Config.get("jvibe.gatewayHost", "127.0.0.1"),
        Config.getInt("jvibe.gatewayPort", 8737),
        List.of(),
        defaultMainTabOrder(),
        Config.getBool("jvibe.monitoringEnabled", true),
        Config.get("jvibe.monitoringHost", "0.0.0.0"),
        Config.getInt("jvibe.monitoringPort", 8738),
        List.of(),
        List.of(),
        List.of());
  }

  public JvibeConfig withTheme(String value) {
    return copy(maxToolIterations, value, mainTabOrder, monitoredServices, sshConnections, sshKeys);
  }

  public JvibeConfig withMaxToolIterations(int value) {
    return copy(value, theme, mainTabOrder, monitoredServices, sshConnections, sshKeys);
  }

  public JvibeConfig withMainTabOrder(List<String> value) {
    return copy(maxToolIterations, theme, value, monitoredServices, sshConnections, sshKeys);
  }

  public JvibeConfig withMonitoredServices(List<MonitoredService> value) {
    return copy(maxToolIterations, theme, mainTabOrder, value, sshConnections, sshKeys);
  }

  public JvibeConfig withSshConfig(List<SshConnection> connections, List<SshKeyRef> keys) {
    return copy(maxToolIterations, theme, mainTabOrder, monitoredServices, connections, keys);
  }

  private JvibeConfig copy(
      int iterations,
      String selectedTheme,
      List<String> tabOrder,
      List<MonitoredService> services,
      List<SshConnection> connections,
      List<SshKeyRef> keys) {
    return new JvibeConfig(
        defaultProvider,
        defaultModel,
        iterations,
        selectedTheme,
        gatewayEnabled,
        gatewayHost,
        gatewayPort,
        deploymentProfiles,
        tabOrder,
        monitoringEnabled,
        monitoringHost,
        monitoringPort,
        services,
        connections,
        keys);
  }

  public String toJson() {
    return io.avaje.jsonb.Jsonb.instance().type(JvibeConfig.class).toJson(this);
  }

  public static JvibeConfig fromJson(String json) {
    return io.avaje.jsonb.Jsonb.instance().type(JvibeConfig.class).fromJson(json);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static List<String> defaultMainTabOrder() {
    return List.of("desktop", "remote-ssh", "agent-orchestrator", "jfr", "stacktrace");
  }
}
