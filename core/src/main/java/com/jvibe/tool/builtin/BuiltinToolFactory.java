package com.jvibe.tool.builtin;

import com.jvibe.git.GitService;
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import java.util.List;

@Factory
public final class BuiltinToolFactory {
  @Bean
  BuiltinToolSet builtinToolSet(GitService git) {
    var fileTools = FileToolSet.workspace();
    return new BuiltinToolSet(List.of(
        ShellRunTool.registered(),
        fileTools.get(0),
        fileTools.get(1),
        fileTools.get(2),
        FilePatchTool.registered(),
        PatchApplyTool.registered(),
        GitStatusTool.registered(git),
        GitDiffTool.registered(git),
        GitCommitTool.registered(git),
        GitPushTool.registered(git),
        ToolManagementTool.registered("tool_list"),
        ToolManagementTool.registered("tool_inspect"),
        ToolManagementTool.registered("tool_create"),
        ToolManagementTool.registered("tool_build"),
        ToolManagementTool.registered("tool_reload"),
        ToolManagementTool.registered("tool_disable"),
        ToolManagementTool.registered("tool_delete"),
        ToolManagementTool.registered("tool_approve")));
  }
}
