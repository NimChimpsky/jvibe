package com.jvibe.deployment;

public record AgentServiceConfig(
    String jarPath,
    String workspace,
    String profile,
    boolean gatewayEnabled,
    String gatewayHost,
    int gatewayPort) {
  public String daemonCommand() {
    return "java -jar " + jarPath + " daemon --workspace " + workspace + " --profile " + profile;
  }
}
