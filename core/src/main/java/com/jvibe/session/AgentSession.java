package com.jvibe.session;

import com.jvibe.tool.ToolRegistry;
import com.jvibe.workspace.Workspace;
import java.util.function.Supplier;

/**
 * One conversation in one tab: its tools, working directory and the context
 * (attached files, servers or connections) sent with every request.
 */
public final class AgentSession {
  private final Mode mode;
  private final Workspace workspace;
  private final ToolRegistry toolRegistry;
  private final Supplier<String> context;
  private final Conversation conversation = new Conversation();

  public AgentSession(Mode mode, Workspace workspace, ToolRegistry toolRegistry, Supplier<String> context) {
    this.mode = mode;
    this.workspace = workspace;
    this.toolRegistry = toolRegistry;
    this.context = context;
  }

  public Mode mode() {
    return mode;
  }

  public Workspace workspace() {
    return workspace;
  }

  public ToolRegistry toolRegistry() {
    return toolRegistry;
  }

  public Conversation conversation() {
    return conversation;
  }

  /** Current session context; evaluated per request so attachments stay up to date. */
  public String context() {
    var value = context.get();
    return value == null ? "" : value;
  }
}
