package com.jvibe.tool.builtin;

import com.jvibe.approval.ApprovalRequest;
import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import java.nio.file.Files;
import java.util.Map;

public final class FilePatchTool {
  private FilePatchTool() {}

  public static RegisteredTool registered() {
    return new RegisteredTool(
        new ToolDefinition("file.patch", "Patch file", "Replace text in a UTF-8 file inside the workspace.", Map.of("type", "object"), ApprovalRequest.Risk.HIGH),
        FilePatchTool::execute);
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context) throws Exception {
    var path = context.workspace().resolveInside(ToolArguments.string(call.arguments(), "path"));
    var search = ToolArguments.string(call.arguments(), "search");
    var replacement = ToolArguments.string(call.arguments(), "replacement", "");
    var content = Files.readString(path);
    if (!content.contains(search)) {
      return ToolResult.failure(call.id(), "Search text not found in " + path.getFileName());
    }
    var updated = content.replace(search, replacement);
    Files.writeString(path, updated);
    return ToolResult.success(call.id(), Map.of("path", path.toString()), "Patched " + path.getFileName());
  }
}
