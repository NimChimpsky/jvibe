package com.jvibe.config;

import java.io.IOException;
import java.nio.file.Files;
import jakarta.inject.Singleton;

@Singleton
public final class ConfigService {
  private static final String DEFAULT_SYSTEM_PROMPT = """
      You are jvibe, a Java-based coding agent. Be direct, inspect the workspace before changing files,
      prefer small safe edits, and ask for approval before risky tool actions.
      """;

  private final JvibePaths paths;

  public ConfigService(JvibePaths paths) {
    this.paths = paths;
  }

  public JvibePaths paths() {
    return paths;
  }

  public void bootstrap() throws IOException {
    Files.createDirectories(paths.home());
    Files.createDirectories(paths.config().getParent());
    Files.createDirectories(paths.providers().getParent());
    Files.createDirectories(paths.systemPrompt().getParent());
    Files.createDirectories(paths.sessions());
    Files.createDirectories(paths.tools());
    Files.createDirectories(paths.logs());

    if (Files.notExists(paths.config())) {
      Files.writeString(paths.config(), JvibeConfig.defaults().toJson());
    }
    if (Files.notExists(paths.providers())) {
      Files.writeString(paths.providers(), "{\n  \"providers\": []\n}\n");
    }
    if (Files.notExists(paths.systemPrompt())) {
      Files.writeString(paths.systemPrompt(), DEFAULT_SYSTEM_PROMPT);
    }
  }

  public String systemPrompt() throws IOException {
    return Files.readString(paths.systemPrompt());
  }
}
