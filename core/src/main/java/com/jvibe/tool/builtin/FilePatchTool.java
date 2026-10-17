package com.jvibe.tool.builtin;

import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolArguments;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import com.jvibe.config.AtomicFiles;
import java.nio.file.Files;
import java.util.Map;

public final class FilePatchTool {
  private FilePatchTool() {}

  public static RegisteredTool registered() {
    return RegisteredTool.changing("file_patch", FilePatchTool::execute);
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context) throws Exception {
    var path = context.workspace().resolveInside(ToolArguments.string(call.arguments(), "path"));
    var search = ToolArguments.string(call.arguments(), "search");
    var replacement = ToolArguments.string(call.arguments(), "replacement", "");
    if (search.isEmpty()) {
      return ToolResult.failure(call.id(), "Search text must not be empty");
    }
    var content = Files.readString(path);
    var first = content.indexOf(search);
    if (first < 0) {
      return ToolResult.failure(call.id(), "Search text not found in " + path.getFileName());
    }
    if (content.indexOf(search, first + 1) >= 0) {
      return ToolResult.failure(call.id(), "Search text occurs more than once in " + path.getFileName()
          + "; include more surrounding context");
    }
    var updated = content.substring(0, first) + replacement + content.substring(first + search.length());
    AtomicFiles.writeString(path, updated);
    return ToolResult.success(call.id(), Map.of("path", path.toString()), "Patched " + path.getFileName());
  }
}
