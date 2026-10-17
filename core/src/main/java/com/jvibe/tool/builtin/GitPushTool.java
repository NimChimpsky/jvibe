package com.jvibe.tool.builtin;

import com.jvibe.git.GitService;
import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import java.util.Map;

public final class GitPushTool {
  private GitPushTool() {}

  public static RegisteredTool registered(GitService git) {
    return new RegisteredTool(
        new ToolDefinition("git_push", "Git push", "Push the current Git branch.", ToolSchemas.object()),
        (call, context) -> execute(call, context, git));
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context, GitService git) throws Exception {
    var result = git.run(context.workspace().root(), new byte[0], context.timeout(), context.cancelled(), "push");
    var payload = Map.<String, Object>of("exitCode", result.exitCode(), "stdout", result.stdout(), "stderr", result.stderr());
    var summary = "Git push exited " + result.exitCode();
    return result.exitCode() == 0 ? ToolResult.success(call.id(), payload, summary)
        : ToolResult.failure(call.id(), payload, summary);
  }
}
