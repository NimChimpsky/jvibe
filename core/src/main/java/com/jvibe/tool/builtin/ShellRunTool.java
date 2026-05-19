package com.jvibe.tool.builtin;

import com.jvibe.approval.ApprovalRequest;
import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class ShellRunTool {
  private ShellRunTool() {}

  public static RegisteredTool registered() {
    return new RegisteredTool(
        new ToolDefinition(
            "shell.run",
            "Run shell command",
            "Run a shell command in the workspace using JVM process APIs.",
            Map.of("type", "object"),
            ApprovalRequest.Risk.HIGH),
        ShellRunTool::execute);
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context) throws Exception {
    var command = ToolArguments.string(call.arguments(), "command");
    var process = new ProcessBuilder("bash", "-lc", command)
        .directory(context.workspace().root().toFile())
        .redirectErrorStream(false)
        .start();
    var finished = process.waitFor(context.timeout().toMillis(), TimeUnit.MILLISECONDS);
    if (!finished) {
      process.destroyForcibly();
      return ToolResult.failure(call.id(), "Command timed out after " + context.timeout().toSeconds() + "s");
    }
    var stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    var stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    return ToolResult.success(
        call.id(),
        Map.of("exitCode", process.exitValue(), "stdout", stdout, "stderr", stderr),
        "Command exited " + process.exitValue());
  }
}
