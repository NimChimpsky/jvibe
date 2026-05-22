package com.jvibe.deployment;

import java.util.List;

public record DeploymentResult(boolean success, String summary, List<String> commands, String serviceConfig) {
  public static DeploymentResult success(String summary, List<String> commands, String serviceConfig) {
    return new DeploymentResult(true, summary, List.copyOf(commands), serviceConfig);
  }

  public static DeploymentResult failure(String summary, List<String> commands) {
    return new DeploymentResult(false, summary, List.copyOf(commands), "");
  }
}
