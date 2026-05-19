package com.jvibe.tool.builtin;

import com.jvibe.approval.ApprovalRequest;
import com.jvibe.git.GitService;
import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import java.util.Map;

public final class GitDiffTool {
  private GitDiffTool() {}

  public static RegisteredTool registered() {
    return new RegisteredTool(
        new ToolDefinition("git.diff", "Git diff", "Show Git diff.", Map.of("type", "object"), ApprovalRequest.Risk.LOW),
        GitDiffTool::execute);
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context) throws Exception {
    var result = new GitService().run(context.workspace().root(), "diff");
    return ToolResult.success(call.id(), Map.of("exitCode", result.exitCode(), "stdout", result.stdout(), "stderr", result.stderr()), "Git diff");
  }
}
