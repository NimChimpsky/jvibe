package com.jvibe.tool.builtin;

import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolArguments;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FileReadTool {
  private static final int DEFAULT_MAX_LINES = 1_000;
  private static final int MAX_LINES = 5_000;
  private FileReadTool() {}

  public static RegisteredTool registered(FileToolAccess access) {
    return RegisteredTool.reading("file_read", (call, context) -> execute(call, context, access));
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context, FileToolAccess access) throws Exception {
    var path = access.resolveReadable(context.workspace(), ToolArguments.string(call.arguments(), "path"));
    if (!Files.isRegularFile(path)) {
      return ToolResult.failure(call.id(), "Not a file: " + path);
    }
    var startLine = Math.max(1, ToolArguments.integer(call.arguments(), "startLine", 1));
    var maxLines = ToolArguments.integer(call.arguments(), "maxLines", DEFAULT_MAX_LINES, 1, MAX_LINES);
    var content = new StringBuilder();
    var currentLine = 0;
    var returned = 0;
    try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {
        currentLine++;
        if (currentLine >= startLine && returned < maxLines) {
          if (returned > 0) content.append('\n');
          content.append(line);
          returned++;
        }
      }
    }
    var payload = new LinkedHashMap<String, Object>();
    payload.put("path", path.toString());
    payload.put("content", content.toString());
    payload.put("startLine", startLine);
    payload.put("endLine", returned == 0 ? startLine - 1 : startLine + returned - 1);
    payload.put("totalLines", currentLine);
    payload.put("truncated", startLine + returned - 1 < currentLine);
    return ToolResult.success(call.id(), payload, "Read " + returned + " lines from " + path.getFileName());
  }
}
