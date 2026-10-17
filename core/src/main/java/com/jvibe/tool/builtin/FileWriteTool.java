package com.jvibe.tool.builtin;

import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolArguments;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import com.jvibe.config.AtomicFiles;
import java.util.Map;

public final class FileWriteTool {
  private FileWriteTool() {}

  public static RegisteredTool registered(FileToolAccess access) {
    return RegisteredTool.changing("file_write", (call, context) -> execute(call, context, access));
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context, FileToolAccess access) throws Exception {
    var path = access.resolveWritable(context.workspace(), ToolArguments.string(call.arguments(), "path"));
    var content = ToolArguments.string(call.arguments(), "content", "");
    AtomicFiles.writeString(path, content);
    return ToolResult.success(
        call.id(),
        Map.of("path", path.toString(), "bytes", content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length),
        "Wrote " + path.getFileName());
  }
}
