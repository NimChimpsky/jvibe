package com.jvibe.tool.builtin;

import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.ArrayList;

public final class FileListTool {
  private static final int DEFAULT_LIMIT = 1_000;
  private static final int MAX_LIMIT = 5_000;
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
            ToolSchemas.object(Map.of(
                "path", ToolSchemas.string("Relative path under the workspace. Defaults to '.'."),
                "limit", ToolSchemas.integer("Maximum entries to return.", 1, MAX_LIMIT)))),
        (call, context) -> execute(call, context, access));
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context, FileToolAccess access) throws Exception {
    var path = ToolArguments.string(call.arguments(), "path", ".");
    var limit = Math.clamp(ToolArguments.integer(call.arguments(), "limit", DEFAULT_LIMIT), 1, MAX_LIMIT);
    if (".".equals(path) || path.isBlank()) {
      var files = new ArrayList<String>();
      for (var root : access.listRoots(context.workspace())) {
        if (Files.isDirectory(root)) {
          try (var stream = Files.list(root)) {
            stream.map(entry -> display(context.workspace(), root, entry)).forEach(files::add);
          }
        } else {
          files.add(root.toString());
        }
      }
      files.sort(String.CASE_INSENSITIVE_ORDER);
      var truncated = files.size() > limit;
      var result = files.stream().limit(limit).toList();
      return ToolResult.success(
          call.id(),
          Map.of("files", result, "truncated", truncated),
          "Listed " + result.size() + (truncated ? " of " + files.size() : "") + " paths");
    }
    var root = access.resolveReadable(context.workspace(), path);
    if (!Files.isDirectory(root)) {
      return ToolResult.failure(call.id(), "Not a directory: " + root);
    }
    try (var stream = Files.list(root)) {
      var files = stream.map(root::relativize).map(Object::toString).sorted().toList();
      var truncated = files.size() > limit;
      var result = files.stream().limit(limit).toList();
      return ToolResult.success(
          call.id(),
          Map.of("files", result, "truncated", truncated),
          "Listed " + result.size() + (truncated ? " of " + files.size() : "") + " files");
    }
  }

  private static String display(com.jvibe.workspace.Workspace workspace, Path root, Path entry) {
    return root.equals(workspace.root()) ? workspace.root().relativize(entry).toString() : entry.toString();
  }
}
