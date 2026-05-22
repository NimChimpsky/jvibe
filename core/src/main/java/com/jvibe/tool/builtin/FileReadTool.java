package com.jvibe.tool.builtin;

import com.jvibe.approval.ApprovalRequest;
import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import java.nio.file.Files;
import java.util.Map;

public final class FileReadTool {
  private FileReadTool() {}

  public static RegisteredTool registered() {
    return new RegisteredTool(
        new ToolDefinition(
            "file_read",
            "Read file",
            "Read a UTF-8 file inside the workspace.",
            ToolSchemas.object(Map.of("path", ToolSchemas.string("Relative file path under the workspace.")), "path"),
            ApprovalRequest.Risk.LOW),
        FileReadTool::execute);
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context) throws Exception {
    var path = context.workspace().resolveInside(ToolArguments.string(call.arguments(), "path"));
    var content = Files.readString(path);
    return ToolResult.success(call.id(), Map.of("path", path.toString(), "content", content), "Read " + path.getFileName());
  }
}
