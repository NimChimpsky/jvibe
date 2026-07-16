package com.jvibe.deployment;

import io.avaje.jsonb.Json;

@Json
public record DeploymentProfile(
    String name,
    DeploymentTarget target,
    String provider,
    String model,
    boolean gatewayEnabled,
    String gatewayHost,
    int gatewayPort) {
  public DeploymentProfile {
    name = blank(name) ? "default" : name;
    target = target == null ? new DeploymentTarget("local", "", "", "~/.jvibe/agent", ".", 22, "") : target;
    provider = blank(provider) ? "jvibe" : provider;
    model = blank(model) ? "jvibe-coder" : model;
    gatewayHost = blank(gatewayHost) ? "127.0.0.1" : gatewayHost;
    gatewayPort = gatewayPort <= 0 ? 8737 : gatewayPort;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
