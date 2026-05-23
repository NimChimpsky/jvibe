package com.jvibe.tool.builtin;

import com.jvibe.git.GitService;
import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import java.util.Map;

public final class GitDiffTool {
  private GitDiffTool() {}

  public static RegisteredTool registered(GitService git) {
    return new RegisteredTool(
        new ToolDefinition("git_diff", "Git diff", "Show Git diff.", ToolSchemas.object()),
        (call, context) -> execute(call, context, git));
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context, GitService git) throws Exception {
    var result = git.run(context.workspace().root(), "diff");
    return ToolResult.success(call.id(), Map.of("exitCode", result.exitCode(), "stdout", result.stdout(), "stderr", result.stderr()), "Git diff");
  }
}
