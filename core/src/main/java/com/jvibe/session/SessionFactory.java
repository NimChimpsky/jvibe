package com.jvibe.session;

import com.jvibe.config.ConfigService;
import com.jvibe.jdbc.JdbcConnectionConfig;
import com.jvibe.jdbc.JdbcToolSet;
import com.jvibe.ssh.SshConnection;
import com.jvibe.ssh.SshToolSet;
import com.jvibe.tool.builtin.FileToolSet;
import com.jvibe.workspace.Workspace;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Creates a session per tab with that tab's tools and request context. */
@Singleton
public final class SessionFactory {
  private final ConfigService config;
  private final FileToolSet fileTools;
  private final SshToolSet sshTools;
  private final JdbcToolSet jdbcTools;

  public SessionFactory(ConfigService config, FileToolSet fileTools, SshToolSet sshTools, JdbcToolSet jdbcTools) {
    this.config = config;
    this.fileTools = fileTools;
    this.sshTools = sshTools;
    this.jdbcTools = jdbcTools;
  }

  /** Chat reads only the attached files and folders. */
  public AgentSession chat(Supplier<List<Path>> attachments) {
    return new AgentSession(Mode.CHAT, scratch(), FileToolSet.chat(attachments), () -> {
      var paths = attachments.get();
      if (paths.isEmpty()) return "";
      var text = new StringBuilder("Attached files and folders (read-only):\n");
      paths.forEach(path -> text.append("- ").append(path).append('\n'));
      return text.toString();
    });
  }

  public AgentSession build(Path repository) {
    var workspace = new Workspace(repository);
    var context = "Repository root: " + workspace.root() + "\n"
        + "Operating system: " + System.getProperty("os.name") + " " + System.getProperty("os.arch") + "\n"
        + "Shell for shell_run: " + (isWindows() ? "PowerShell" : "/bin/sh") + "\n"
        + "Client JDK: " + Runtime.version() + "\n";
    return new AgentSession(Mode.BUILD, workspace, fileTools.build(), () -> context);
  }

  public AgentSession servers(Supplier<List<SshConnection>> attached) {
    return new AgentSession(Mode.SERVERS, scratch(), sshTools.registry(attached),
        () -> SshToolSet.context(attached.get()));
  }

  public AgentSession databases(Supplier<List<JdbcConnectionConfig>> attached) {
    return new AgentSession(Mode.DATABASES, scratch(), jdbcTools.registry(attached),
        () -> JdbcToolSet.context(attached.get()));
  }

  private Workspace scratch() {
    return new Workspace(config.paths().tmp());
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }
}
