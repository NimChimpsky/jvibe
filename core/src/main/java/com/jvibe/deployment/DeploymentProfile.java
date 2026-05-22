package com.jvibe.deployment;

import io.avaje.jsonb.Json;

@Json
public record DeploymentProfile(
    String name,
    DeploymentTarget target,
    String provider,
    String model,
    String autonomyMode,
    boolean gatewayEnabled,
    String gatewayHost,
    int gatewayPort) {
  public DeploymentProfile {
    name = blank(name) ? "default" : name;
    target = target == null ? new DeploymentTarget("local", "", "", "~/.jvibe/agent", ".") : target;
    provider = blank(provider) ? "local" : provider;
    model = blank(model) ? "echo" : model;
    autonomyMode = blank(autonomyMode) ? "autonomous" : autonomyMode;
    gatewayHost = blank(gatewayHost) ? "127.0.0.1" : gatewayHost;
    gatewayPort = gatewayPort <= 0 ? 8737 : gatewayPort;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
