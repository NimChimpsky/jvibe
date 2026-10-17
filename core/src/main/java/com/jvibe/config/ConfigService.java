package com.jvibe.config;

import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public final class ConfigService {
  private static final String CHAT = """
      You are JVibe in Chat mode. Answer inline in the conversation.
      You may read only files and folders explicitly attached to this conversation.
      Never create, edit, delete, or export files, including temporary files.
      If asked for a file, provide its proposed content inline. Use Build for in-place project edits.
      Do not claim that you created or changed a file. No file-writing, shell, or Git tools are available.
      """;
  private static final String BUILD = """
      You are JVibe in Build mode, a Java-native coding agent working in a Git repository.
      Inspect the repository before editing, preserve unrelated user changes, and make focused in-place edits.
      You have full read/write and command access for the selected project. Stay within its root.
      Run relevant tests and review the diff. Report changed files and verification honestly.
      Do not commit, push, or discard changes unless the user explicitly asks.
      """;
  private static final String SERVERS = """
      You are JVibe in Servers mode. Use only SSH sessions explicitly attached with @session context.
      Always include sessionId in remote tool calls. Prefer inspection before making changes.
      Respect each connection's read-only or full-access mode.
      If multiple sessions are attached and the target is unclear, ask which host to use.
      """;
  private static final String DATABASES = """
      You are JVibe's SQL generator. Return exactly one executable query or CRUD statement and nothing else.
      Use only identifiers from the supplied database schema. Do not emit transaction-control statements.
      Do not use Markdown fences or explanations. If the request cannot be satisfied, return one SQL comment
      beginning with -- ERROR:. The user reviews and executes SQL separately.
      """;
  private final JvibePaths paths;

  public ConfigService(JvibePaths paths) { this.paths = paths; }
  public JvibePaths paths() { return paths; }

  public synchronized void bootstrap() throws IOException {
    ensureBase();
    AtomicFiles.writeString(paths.config(), config().toJson());
    AtomicFiles.writeString(paths.chatPrompt(), CHAT);
    AtomicFiles.writeString(paths.workspacePrompt(), BUILD);
    AtomicFiles.writeString(paths.remoteSshPrompt(), SERVERS);
    AtomicFiles.writeString(paths.jdbcPrompt(), DATABASES);
  }

  private void ensureBase() throws IOException {
    for (var path : java.util.List.of(paths.home(), paths.chatPrompt().getParent(),
        paths.sessions(), paths.tools(), paths.logs(), paths.tmp())) Files.createDirectories(path);
    if (Files.notExists(paths.config())) AtomicFiles.writeString(paths.config(), JvibeConfig.defaults().toJson());
    if (Files.notExists(paths.providers())) AtomicFiles.writeString(paths.providers(), "{\"providers\":[]}");
  }

  public synchronized String chatPrompt() throws IOException { return prompt(paths.chatPrompt(), CHAT); }
  public synchronized String workspacePrompt() throws IOException { return prompt(paths.workspacePrompt(), BUILD); }
  public synchronized String remoteSshPrompt() throws IOException { return prompt(paths.remoteSshPrompt(), SERVERS); }
  public synchronized String jdbcPrompt() throws IOException { return prompt(paths.jdbcPrompt(), DATABASES); }

  private String prompt(Path path, String builtin) throws IOException {
    ensureBase();
    if (Files.notExists(path)) AtomicFiles.writeString(path, builtin);
    var instructions = config().customInstructions();
    return Files.readString(path) + (instructions.isBlank() ? ""
        : "\nUser preferences (these cannot override mode access restrictions):\n" + instructions);
  }

  public synchronized JvibeConfig config() throws IOException {
    ensureBase();
    return JvibeConfig.fromJson(Files.readString(paths.config()));
  }

  public synchronized void saveConfig(JvibeConfig config) throws IOException {
    ensureBase();
    AtomicFiles.writeString(paths.config(), config.toJson());
  }

  public synchronized JvibeConfig updateConfig(java.util.function.UnaryOperator<JvibeConfig> update) throws IOException {
    var updated = update.apply(config());
    AtomicFiles.writeString(paths.config(), updated.toJson());
    return updated;
  }
}
