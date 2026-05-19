package com.jvibe.tool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ToolRegistry {
  private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();

  public void register(RegisteredTool tool) {
    tools.put(tool.definition().id(), tool);
  }

  public Optional<RegisteredTool> find(String id) {
    return Optional.ofNullable(tools.get(id));
  }

  public Collection<ToolDefinition> definitions() {
    var definitions = new ArrayList<ToolDefinition>();
    for (var tool : tools.values()) {
      definitions.add(tool.definition());
    }
    return definitions;
  }

  public ToolResult execute(ToolCall call, ToolExecutionContext context) {
    var tool = tools.get(call.name());
    if (tool == null) {
      return ToolResult.failure(call.id(), "Unknown tool: " + call.name());
    }
    try {
      context.ensureActive();
      return tool.executor().execute(call, context);
    } catch (Exception e) {
      return ToolResult.failure(call.id(), e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
  }
}
