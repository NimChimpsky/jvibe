package com.jvibe.config;

import java.io.IOException;
import java.nio.file.Files;
import jakarta.inject.Singleton;

@Singleton
public final class ConfigService {
  private static final String DEFAULT_CHAT_PROMPT = """
      You are jvibe in default Chat mode. Be direct and answer simple conversation without tools when no file work is needed.
      You may read only files and folders explicitly added to chat context, plus files under ~/.jvibe/tmp.
      You may write, create, or delete files only under ~/.jvibe/tmp. Never edit attached source files in place from Chat mode.
      Put every generated file, summary, report, patch draft, export, or other output under ~/.jvibe/tmp so it appears in the Output panel.
      If the user asks to create summary.md, output/summary.md, or any other relative output path, write that path under ~/.jvibe/tmp.
      Tell the user to create a workspace tab for full in-place file editing.
      """;
  private static final String DEFAULT_WORKSPACE_PROMPT = """
      You are jvibe in Workspace mode, a Java-based coding agent. Be direct, inspect the workspace before changing files,
      and prefer small safe edits.
      Reads and writes are rooted at the selected workspace folder.
      Edit files in place under the selected workspace root when the user asks for code or file changes.
      If the user asks to create summary.md or any other relative output path, write it relative to the workspace root unless they specify a subpath.
      When summarising a directory and no output path is specified, create summary.md in the workspace root by default.
      Respect workspace boundaries and ask for approval before risky tool actions.
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
    Files.createDirectories(paths.chatPrompt().getParent());
    Files.createDirectories(paths.sessions());
    Files.createDirectories(paths.tools());
    Files.createDirectories(paths.logs());
    Files.createDirectories(paths.tmp());

    if (Files.notExists(paths.config())) {
      Files.writeString(paths.config(), JvibeConfig.defaults().toJson());
    } else {
      Files.writeString(paths.config(), JvibeConfig.fromJson(Files.readString(paths.config())).toJson());
    }
    if (Files.notExists(paths.providers())) {
      Files.writeString(paths.providers(), "{\n  \"providers\": []\n}\n");
    }
    Files.writeString(paths.chatPrompt(), DEFAULT_CHAT_PROMPT);
    Files.writeString(paths.workspacePrompt(), DEFAULT_WORKSPACE_PROMPT);
    Files.deleteIfExists(paths.home().resolve("prompts").resolve("system.md"));
  }

  public String chatPrompt() throws IOException {
    bootstrap();
    return Files.readString(paths.chatPrompt());
  }

  public String workspacePrompt() throws IOException {
    bootstrap();
    return Files.readString(paths.workspacePrompt());
  }

  public JvibeConfig config() throws IOException {
    if (Files.notExists(paths.config())) {
      bootstrap();
    }
    return JvibeConfig.fromJson(Files.readString(paths.config()));
  }

  public void saveConfig(JvibeConfig config) throws IOException {
    bootstrap();
    Files.writeString(paths.config(), config.toJson());
  }
}
