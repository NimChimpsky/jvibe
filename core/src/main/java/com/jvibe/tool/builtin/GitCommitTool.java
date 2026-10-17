package com.jvibe.tool.builtin;

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
            ToolSchemas.object(Map.of("message", ToolSchemas.string("Commit message.")), "message")),
        (call, context) -> execute(call, context, git));
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context, GitService git) throws Exception {
    var message = ToolArguments.string(call.arguments(), "message");
    var add = git.run(context.workspace().root(), new byte[0], context.timeout(), context.cancelled(), "add", "-A");
    if (add.exitCode() != 0) {
      return ToolResult.failure(call.id(), add.stderr());
    }
    context.ensureActive();
    var commit = git.run(context.workspace().root(), new byte[0], context.timeout(), context.cancelled(), "commit", "-m", message);
    var payload = Map.<String, Object>of("exitCode", commit.exitCode(), "stdout", commit.stdout(), "stderr", commit.stderr());
    var summary = "Git commit exited " + commit.exitCode();
    return commit.exitCode() == 0 ? ToolResult.success(call.id(), payload, summary)
        : ToolResult.failure(call.id(), payload, summary);
  }
}
