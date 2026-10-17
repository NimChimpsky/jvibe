package com.jvibe.tool;

/**
 * A client-side tool implementation. Schemas are defined by the JVibe service; the client
 * matches calls by name. Tools that change files, servers or data can require approval.
 */
public record RegisteredTool(String name, boolean changesState, ToolExecutor executor) {
  public static RegisteredTool reading(String name, ToolExecutor executor) {
    return new RegisteredTool(name, false, executor);
  }

  public static RegisteredTool changing(String name, ToolExecutor executor) {
    return new RegisteredTool(name, true, executor);
  }
}
