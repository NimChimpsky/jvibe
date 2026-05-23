package com.jvibe.session;

import com.jvibe.tool.ToolRegistry;
import com.jvibe.workspace.Workspace;
import java.util.UUID;

public final class AgentSession {
  public enum Mode {
    CHAT,
    WORKSPACE
  }

  private final String id;
  private final Workspace workspace;
  private final Conversation conversation;
  private final ToolRegistry toolRegistry;
  private final Mode mode;
  private String provider;
  private String model;

  public AgentSession(Workspace workspace, ToolRegistry toolRegistry) {
    this(UUID.randomUUID().toString(), workspace, toolRegistry, "local", "echo", Mode.WORKSPACE);
  }

  public AgentSession(String id, Workspace workspace, ToolRegistry toolRegistry, String provider, String model) {
    this(id, workspace, toolRegistry, provider, model, Mode.WORKSPACE);
  }

  public AgentSession(Workspace workspace, ToolRegistry toolRegistry, Mode mode) {
    this(UUID.randomUUID().toString(), workspace, toolRegistry, "local", "echo", mode);
  }

  public AgentSession(String id, Workspace workspace, ToolRegistry toolRegistry, String provider, String model, Mode mode) {
    this.id = id;
    this.workspace = workspace;
    this.toolRegistry = toolRegistry;
    this.mode = mode == null ? Mode.WORKSPACE : mode;
    this.provider = provider;
    this.model = model;
    this.conversation = new Conversation();
  }

  public String id() {
    return id;
  }

  public Workspace workspace() {
    return workspace;
  }

  public Conversation conversation() {
    return conversation;
  }

  public ToolRegistry toolRegistry() {
    return toolRegistry;
  }

  public Mode mode() {
    return mode;
  }

  public String provider() {
    return provider;
  }

  public String model() {
    return model;
  }

  public void selectModel(String provider, String model) {
    this.provider = provider;
    this.model = model;
  }
}
