package com.jvibe.tool.builtin;

import com.jvibe.git.GitService;
import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolArguments;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import java.util.List;

/** git_status, git_diff, git_commit and git_push for the Build tab. */
final class GitTools {
  private GitTools() {}

  static List<RegisteredTool> all(GitService git) {
    return List.of(
        RegisteredTool.reading("git_status", (call, context) ->
            run(git, call, context, "Git status", "status", "--short", "--branch")),
        RegisteredTool.reading("git_diff", (call, context) -> run(git, call, context, "Git diff", "diff")),
        RegisteredTool.changing("git_commit", (call, context) -> commit(git, call, context)),
        RegisteredTool.changing("git_push", (call, context) -> run(git, call, context, "Git push", "push")));
  }

  private static ToolResult run(GitService git, ToolCall call, ToolExecutionContext context, String label,
      String... args) throws Exception {
    var result = git.run(context.workspace().root(), new byte[0], context.timeout(), context.cancelled(), args);
    return ToolResult.exit(call.id(), result.exitCode(), result.stdout(), result.stderr(),
        label + " exited " + result.exitCode());
  }

  private static ToolResult commit(GitService git, ToolCall call, ToolExecutionContext context) throws Exception {
    var message = ToolArguments.string(call.arguments(), "message");
    var add = run(git, call, context, "Git add", "add", "-A");
    if (!add.success()) return add;
    context.ensureActive();
    return run(git, call, context, "Git commit", "commit", "-m", message);
  }
}
