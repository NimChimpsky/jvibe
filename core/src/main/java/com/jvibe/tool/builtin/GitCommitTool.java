package com.jvibe.tool.builtin;

import com.jvibe.approval.ApprovalRequest;
import com.jvibe.git.GitService;
import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import java.util.Map;

public final class GitCommitTool {
  private GitCommitTool() {}

  public static RegisteredTool registered(GitService git) {
    return new RegisteredTool(
        new ToolDefinition(
            "git_commit",
            "Git commit",
            "Stage all current changes and create a Git commit.",
            ToolSchemas.object(Map.of("message", ToolSchemas.string("Commit message.")), "message"),
            ApprovalRequest.Risk.HIGH),
        (call, context) -> execute(call, context, git));
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context, GitService git) throws Exception {
    var message = ToolArguments.string(call.arguments(), "message");
    var add = git.run(context.workspace().root(), "add", "-A");
    if (add.exitCode() != 0) {
      return ToolResult.failure(call.id(), add.stderr());
    }
    var commit = git.run(context.workspace().root(), "commit", "-m", message);
    return ToolResult.success(
        call.id(),
        Map.of("exitCode", commit.exitCode(), "stdout", commit.stdout(), "stderr", commit.stderr()),
        "Git commit exited " + commit.exitCode());
  }
}
