package com.jvibe.tool.builtin;

import com.jvibe.git.GitService;
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import java.util.List;

@Factory
public final class BuiltinToolFactory {
  @Bean
  BuiltinToolSet builtinToolSet(GitService git, com.jvibe.process.ProcessRunner processes) {
    var fileTools = FileToolSet.workspace();
    return new BuiltinToolSet(List.of(
        ShellRunTool.registered(processes),
        fileTools.get(0),
        fileTools.get(1),
        fileTools.get(2),
        FilePatchTool.registered(),
        PatchApplyTool.registered(git),
        GitStatusTool.registered(git),
        GitDiffTool.registered(git),
        GitCommitTool.registered(git),
        GitPushTool.registered(git)));
  }
}
