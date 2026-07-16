package com.jvibe.deployment;

import io.avaje.jsonb.Json;

@Json
public record DeploymentTarget(
    String type,
    String host,
    String user,
    String installPath,
    String workspace,
    int port,
    String keyPath) {
  public DeploymentTarget {
    type = blank(type) ? "local" : type;
    installPath = blank(installPath) ? "~/.jvibe/agent" : installPath;
    workspace = blank(workspace) ? "." : workspace;
    port = port <= 0 ? 22 : port;
    keyPath = keyPath == null ? "" : keyPath;
  }

  public DeploymentTarget(String type, String host, String user, String installPath, String workspace) {
    this(type, host, user, installPath, workspace, 22, "");
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
