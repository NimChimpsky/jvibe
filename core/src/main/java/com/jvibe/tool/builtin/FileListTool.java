package com.jvibe.tool.builtin;

import com.jvibe.approval.ApprovalRequest;
import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import java.nio.file.Files;
import java.util.Map;

public final class FileListTool {
  private FileListTool() {}

  public static RegisteredTool registered() {
    return new RegisteredTool(
        new ToolDefinition("file.list", "List files", "List files under the workspace.", Map.of("type", "object"), ApprovalRequest.Risk.LOW),
        FileListTool::execute);
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context) throws Exception {
    var path = ToolArguments.string(call.arguments(), "path", ".");
    var root = context.workspace().resolveInside(path);
    try (var stream = Files.list(root)) {
      var files = stream.map(root::relativize).map(Object::toString).sorted().toList();
      return ToolResult.success(call.id(), Map.of("files", files), "Listed " + files.size() + " files");
    }
  }
}
