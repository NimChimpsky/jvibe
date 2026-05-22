package com.jvibe.deployment;

import io.avaje.jsonb.Json;

@Json
public record DeploymentTarget(
    String type,
    String host,
    String user,
    String installPath,
    String workspace) {
  public DeploymentTarget {
    type = blank(type) ? "local" : type;
    installPath = blank(installPath) ? "~/.jvibe/agent" : installPath;
    workspace = blank(workspace) ? "." : workspace;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
