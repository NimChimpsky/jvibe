package com.jvibe.tool.builtin;

import com.jvibe.tool.RegisteredTool;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public final class FileToolSet {
  private FileToolSet() {}

  public static List<RegisteredTool> workspace() {
    var access = FileToolAccess.workspace();
    return List.of(
        FileListTool.registered(access),
        FileReadTool.registered(access),
        FileWriteTool.registered(access, "Write file", "Write a UTF-8 file inside the workspace."));
  }

  public static List<RegisteredTool> defaultChat(Path tmp, Supplier<List<Path>> references) {
    var access = FileToolAccess.defaultChat(tmp, references);
    return List.of(
        FileListTool.registered(access),
        FileReadTool.registered(access),
        FileWriteTool.registered(access, "Write output file", "Write a UTF-8 output file under ~/.jvibe/tmp."));
  }
}
