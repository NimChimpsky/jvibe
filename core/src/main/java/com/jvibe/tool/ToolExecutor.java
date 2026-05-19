package com.jvibe.tool;

@FunctionalInterface
public interface ToolExecutor {
  ToolResult execute(ToolCall call, ToolExecutionContext context) throws Exception;
}
