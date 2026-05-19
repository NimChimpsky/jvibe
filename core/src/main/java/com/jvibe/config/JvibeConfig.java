package com.jvibe.config;

import io.avaje.config.Config;
import io.avaje.jsonb.Json;

@Json
public record JvibeConfig(String defaultProvider, String defaultModel, boolean requireApproval, int maxToolIterations) {
  public static JvibeConfig defaults() {
    return new JvibeConfig(
        Config.get("jvibe.defaultProvider", "local"),
        Config.get("jvibe.defaultModel", "echo"),
        Config.getBool("jvibe.requireApproval", true),
        Config.getInt("jvibe.maxToolIterations", 10));
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
