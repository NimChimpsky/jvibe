package com.jvibe.tool.builtin;

import com.jvibe.approval.ApprovalRequest;
import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import java.nio.file.Files;
import java.util.Map;

public final class FileWriteTool {
  private FileWriteTool() {}

  public static RegisteredTool registered() {
    return new RegisteredTool(
        new ToolDefinition("file.write", "Write file", "Write a UTF-8 file inside the workspace.", Map.of("type", "object"), ApprovalRequest.Risk.HIGH),
        FileWriteTool::execute);
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context) throws Exception {
    var path = context.workspace().resolveInside(ToolArguments.string(call.arguments(), "path"));
    var content = ToolArguments.string(call.arguments(), "content", "");
    Files.createDirectories(path.getParent());
    Files.writeString(path, content);
    return ToolResult.success(call.id(), Map.of("path", path.toString(), "bytes", content.getBytes().length), "Wrote " + path.getFileName());
  }
}
