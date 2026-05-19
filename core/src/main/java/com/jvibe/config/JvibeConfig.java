package com.jvibe.config;

public record JvibeConfig(String defaultProvider, String defaultModel, boolean requireApproval, int maxToolIterations) {
  public static JvibeConfig defaults() {
    return new JvibeConfig("local", "echo", true, 10);
  }

  public String toJson() {
    return """
        {
          "defaultProvider": "%s",
          "defaultModel": "%s",
          "requireApproval": %s,
          "maxToolIterations": %d
        }
        """.formatted(defaultProvider, defaultModel, requireApproval, maxToolIterations);
  }
}
