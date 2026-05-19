package com.jvibe.tool.builtin;

import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolRegistry;

public final class BuiltinTools {
  private BuiltinTools() {}

  public static ToolRegistry registry() {
    var registry = new ToolRegistry();
    for (var tool : builtins()) {
      registry.register(tool);
    }
    return registry;
  }

  public static Iterable<RegisteredTool> builtins() {
    return java.util.List.of(
        ShellRunTool.registered(),
        FileListTool.registered(),
        FileReadTool.registered(),
        FileWriteTool.registered(),
        FilePatchTool.registered(),
        GitStatusTool.registered(),
        GitDiffTool.registered(),
        GitCommitTool.registered(),
        GitPushTool.registered(),
        ToolManagementTool.registered("tool.list"),
        ToolManagementTool.registered("tool.inspect"),
        ToolManagementTool.registered("tool.create"),
        ToolManagementTool.registered("tool.build"),
        ToolManagementTool.registered("tool.reload"),
        ToolManagementTool.registered("tool.disable"),
        ToolManagementTool.registered("tool.delete"),
        ToolManagementTool.registered("tool.approve"));
  }
}
