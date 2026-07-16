package com.jvibe.config;

import java.io.IOException;
import java.nio.file.Files;
import jakarta.inject.Singleton;

@Singleton
public final class ConfigService {
  private static final String DEFAULT_CHAT_PROMPT = """
      You are JVibe in default Chat mode. Be direct and answer simple conversation without tools when no file work is needed.
      You may read only files and folders explicitly added to chat context, plus files under ~/.jvibe/tmp.
      You may write, create, or delete files only under ~/.jvibe/tmp. Never edit attached source files in place from Chat mode.
      Put every generated file, summary, report, patch draft, export, or other output under ~/.jvibe/tmp so it appears in the File Output panel.
      If the user asks to create summary.md, output/summary.md, or any other relative output path, write that path under ~/.jvibe/tmp.
      Tell the user to create a workspace tab for full in-place file editing.
      """;
  private static final String DEFAULT_WORKSPACE_PROMPT = """
      You are JVibe in Workspace mode, a Java-based coding agent. Be direct, inspect the workspace before changing files,
      and prefer small safe edits.
      Reads and writes are rooted at the selected workspace folder.
      Edit files in place under the selected workspace root when the user asks for code or file changes.
      If the user asks to create summary.md or any other relative output path, write it relative to the workspace root unless they specify a subpath.
      When summarising a directory and no output path is specified, create summary.md in the workspace root by default.
      You have full read and write access within the workspace, but never access paths outside its boundary.
      """;
  private static final String DEFAULT_REMOTE_SSH_PROMPT = """
      You are JVibe in Remote SSH mode. Use only SSH sessions explicitly attached to the chat with @session context.
      Always include sessionId when calling remote SSH tools.
      Prefer read-only inspection commands. If a session is read-only, do not attempt mutating commands.
      If multiple SSH sessions are attached and the target is unclear, ask which host to use before running tools.
      """;

  private final JvibePaths paths;

  public ConfigService(JvibePaths paths) {
    this.paths = paths;
  }

  public JvibePaths paths() {
    return paths;
  }

  public synchronized void bootstrap() throws IOException {
    ensureBase();
    AtomicFiles.writeString(paths.config(), JvibeConfig.fromJson(Files.readString(paths.config())).toJson());
    AtomicFiles.writeString(paths.chatPrompt(), DEFAULT_CHAT_PROMPT);
    AtomicFiles.writeString(paths.workspacePrompt(), DEFAULT_WORKSPACE_PROMPT);
    AtomicFiles.writeString(paths.remoteSshPrompt(), DEFAULT_REMOTE_SSH_PROMPT);
    Files.deleteIfExists(paths.home().resolve("prompts").resolve("system.md"));
  }

  private void ensureBase() throws IOException {
    Files.createDirectories(paths.home());
    Files.createDirectories(paths.config().getParent());
    Files.createDirectories(paths.providers().getParent());
    Files.createDirectories(paths.chatPrompt().getParent());
    Files.createDirectories(paths.sessions());
    Files.createDirectories(paths.tools());
    Files.createDirectories(paths.logs());
    Files.createDirectories(paths.tmp());

    if (Files.notExists(paths.config())) {
      AtomicFiles.writeString(paths.config(), JvibeConfig.defaults().toJson());
    }
    if (Files.notExists(paths.providers())) {
      AtomicFiles.writeString(paths.providers(), "{\n  \"providers\": []\n}\n");
    }
  }

  public synchronized String chatPrompt() throws IOException {
    ensurePrompt(paths.chatPrompt(), DEFAULT_CHAT_PROMPT);
    return Files.readString(paths.chatPrompt());
  }

  public synchronized String workspacePrompt() throws IOException {
    ensurePrompt(paths.workspacePrompt(), DEFAULT_WORKSPACE_PROMPT);
    return Files.readString(paths.workspacePrompt());
  }

  public synchronized String remoteSshPrompt() throws IOException {
    ensurePrompt(paths.remoteSshPrompt(), DEFAULT_REMOTE_SSH_PROMPT);
    return Files.readString(paths.remoteSshPrompt());
  }

  public synchronized JvibeConfig config() throws IOException {
    ensureBase();
    return JvibeConfig.fromJson(Files.readString(paths.config()));
  }

  public synchronized void saveConfig(JvibeConfig config) throws IOException {
    ensureBase();
    AtomicFiles.writeString(paths.config(), config.toJson());
  }

  public synchronized JvibeConfig updateConfig(
      java.util.function.UnaryOperator<JvibeConfig> update) throws IOException {
    var updated = update.apply(config());
    AtomicFiles.writeString(paths.config(), updated.toJson());
    return updated;
  }

  private void ensurePrompt(java.nio.file.Path path, String content) throws IOException {
    ensureBase();
    if (Files.notExists(path)) {
      AtomicFiles.writeString(path, content);
    }
  }
}
