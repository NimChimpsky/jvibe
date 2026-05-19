package com.jvibe.tool.dynamic;

import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolResult;

public interface ToolPlugin {
  ToolDefinition definition();

  ToolResult execute(ToolCall call, ToolPluginContext context) throws Exception;
}
