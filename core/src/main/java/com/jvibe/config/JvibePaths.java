package com.jvibe.config;

import java.nio.file.Path;

public record JvibePaths(
    Path home,
    Path config,
    Path providers,
    Path chatPrompt,
    Path workspacePrompt,
    Path remoteSshPrompt,
    Path sessions,
    Path tools,
    Path logs,
    Path tmp) {
  public static JvibePaths under(Path home) {
    var root = home.resolve(".jvibe");
    var prompts = root.resolve("prompts");
    return new JvibePaths(
        root,
        root.resolve("config.json"),
        root.resolve("providers.json"),
        prompts.resolve("chat.md"),
        prompts.resolve("workspace.md"),
        prompts.resolve("remote-ssh.md"),
        root.resolve("sessions"),
        root.resolve("tools"),
        root.resolve("logs"),
        root.resolve("tmp"));
  }

  public static JvibePaths userHome() {
    return under(Path.of(System.getProperty("user.home")));
  }
}
