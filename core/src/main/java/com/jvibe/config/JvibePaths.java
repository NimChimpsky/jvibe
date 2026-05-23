package com.jvibe.config;

import java.nio.file.Path;

public record JvibePaths(Path home, Path config, Path providers, Path systemPrompt, Path sessions, Path tools, Path logs, Path tmp) {
  public static JvibePaths under(Path home) {
    var root = home.resolve(".jvibe");
    return new JvibePaths(
        root,
        root.resolve("config.json"),
        root.resolve("providers.json"),
        root.resolve("prompts").resolve("system.md"),
        root.resolve("sessions"),
        root.resolve("tools"),
        root.resolve("logs"),
        root.resolve("tmp"));
  }

  public static JvibePaths userHome() {
    return under(Path.of(System.getProperty("user.home")));
  }
}
