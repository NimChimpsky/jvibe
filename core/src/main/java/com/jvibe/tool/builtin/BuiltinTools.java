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
        PatchApplyTool.registered(),
        GitStatusTool.registered(),
        GitDiffTool.registered(),
        GitCommitTool.registered(),
        GitPushTool.registered(),
        ToolManagementTool.registered("tool_list"),
        ToolManagementTool.registered("tool_inspect"),
        ToolManagementTool.registered("tool_create"),
        ToolManagementTool.registered("tool_build"),
        ToolManagementTool.registered("tool_reload"),
        ToolManagementTool.registered("tool_disable"),
        ToolManagementTool.registered("tool_delete"),
        ToolManagementTool.registered("tool_approve"));
  }
}
