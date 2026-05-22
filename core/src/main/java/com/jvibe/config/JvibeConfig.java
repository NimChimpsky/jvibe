package com.jvibe.config;

import com.jvibe.deployment.DeploymentProfile;
import io.avaje.config.Config;
import io.avaje.jsonb.Json;
import java.util.List;

@Json
public record JvibeConfig(
    String defaultProvider,
    String defaultModel,
    boolean requireApproval,
    int maxToolIterations,
    String theme,
    String autonomyMode,
    List<String> allowedHighRiskTools,
    boolean gatewayEnabled,
    String gatewayHost,
    int gatewayPort,
    List<DeploymentProfile> deploymentProfiles) {
  public JvibeConfig {
    defaultProvider = blank(defaultProvider) ? "local" : defaultProvider;
    defaultModel = blank(defaultModel) ? "echo" : defaultModel;
    maxToolIterations = maxToolIterations <= 0 ? 10 : maxToolIterations;
    theme = blank(theme) ? "FLATLAF_DARK" : theme;
    autonomyMode = blank(autonomyMode) ? "supervised" : autonomyMode;
    allowedHighRiskTools = allowedHighRiskTools == null ? List.of() : List.copyOf(allowedHighRiskTools);
    gatewayHost = blank(gatewayHost) ? "127.0.0.1" : gatewayHost;
    gatewayPort = gatewayPort <= 0 ? 8737 : gatewayPort;
    deploymentProfiles = deploymentProfiles == null ? List.of() : List.copyOf(deploymentProfiles);
  }

  public static JvibeConfig defaults() {
    return new JvibeConfig(
        Config.get("jvibe.defaultProvider", "local"),
        Config.get("jvibe.defaultModel", "echo"),
        Config.getBool("jvibe.requireApproval", true),
        Config.getInt("jvibe.maxToolIterations", 10),
        Config.get("jvibe.theme", "FLATLAF_DARK"),
        Config.get("jvibe.autonomyMode", "supervised"),
        List.of(),
        Config.getBool("jvibe.gatewayEnabled", false),
        Config.get("jvibe.gatewayHost", "127.0.0.1"),
        Config.getInt("jvibe.gatewayPort", 8737),
        List.of());
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
}
