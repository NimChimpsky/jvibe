package com.jvibe.tool.builtin;

import com.jvibe.approval.ApprovalRequest;
import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class FileListTool {
  private FileListTool() {}

  public static RegisteredTool registered() {
    return registered(FileToolAccess.workspace());
  }

  public static RegisteredTool registered(FileToolAccess access) {
    return new RegisteredTool(
        new ToolDefinition(
            "file_list",
            "List files",
            "List files under the workspace. Use path '.' for the workspace root.",
            ToolSchemas.object(Map.of("path", ToolSchemas.string("Relative path under the workspace. Defaults to '.'."))),
            ApprovalRequest.Risk.LOW),
        (call, context) -> execute(call, context, access));
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context, FileToolAccess access) throws Exception {
    var path = ToolArguments.string(call.arguments(), "path", ".");
    if (".".equals(path) || path.isBlank()) {
      var roots = access.listRoots(context.workspace()).stream().map(Path::toString).sorted().toList();
      return ToolResult.success(call.id(), Map.of("files", roots), "Listed " + roots.size() + " paths");
    }
    var root = access.resolveReadable(context.workspace(), path);
    if (!Files.isDirectory(root)) {
      return ToolResult.failure(call.id(), "Not a directory: " + root);
    }
    try (var stream = Files.list(root)) {
      var files = stream.map(root::relativize).map(Object::toString).sorted().toList();
      return ToolResult.success(call.id(), Map.of("files", files), "Listed " + files.size() + " files");
    }
  }
}
