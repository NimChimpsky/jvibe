package com.jvibe.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ToolRegistry {
  private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();

  public ToolRegistry(List<RegisteredTool> tools) {
    tools.forEach(this::register);
  }

  public ToolRegistry() {
    this(List.of());
  }

  public void register(RegisteredTool tool) {
    tools.put(tool.name(), tool);
  }

  public Optional<RegisteredTool> find(String name) {
    return Optional.ofNullable(tools.get(name));
  }

  public ToolResult execute(ToolCall call, ToolExecutionContext context) {
    var tool = tools.get(call.name());
    if (tool == null) {
      return ToolResult.failure(call.id(), "Unknown tool: " + call.name());
    }
    try {
      context.ensureActive();
      return tool.executor().execute(call, context);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return ToolResult.failure(call.id(), "Tool execution cancelled");
    } catch (Exception e) {
      return ToolResult.failure(call.id(), e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
  }
}
