package com.jvibe.tool.builtin;

import com.jvibe.approval.ApprovalRequest;
import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolResult;
import java.util.Map;

public final class ToolManagementTool {
  private ToolManagementTool() {}

  public static RegisteredTool registered(String id) {
    return new RegisteredTool(
        new ToolDefinition(id, id, "Manage dynamic Java tools.", Map.of("type", "object"), risk(id)),
        (call, context) -> ToolResult.success(
            call.id(),
            Map.of("tool", id, "workspace", context.workspace().root().toString()),
            id + " is exposed; registry wiring is handled by CLI/GUI services."));
  }

  private static ApprovalRequest.Risk risk(String id) {
    return switch (id) {
      case "tool.list", "tool.inspect" -> ApprovalRequest.Risk.LOW;
      default -> ApprovalRequest.Risk.HIGH;
    };
  }
}
