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
  private static final long POLL_MILLIS = 50;
  private static final long SHUTDOWN_GRACE_MILLIS = 1_000;

  private ShellRunTool() {}

  public static RegisteredTool registered() {
    return new RegisteredTool(
        new ToolDefinition(
            "shell_run",
            "Run shell command",
            "Run a shell command in the workspace using JVM process APIs.",
            ToolSchemas.object(Map.of("command", ToolSchemas.string("Shell command to run from the workspace root.")), "command"),
            ApprovalRequest.Risk.HIGH),
        ShellRunTool::execute);
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context) throws Exception {
    var command = ToolArguments.string(call.arguments(), "command");
    var process = new ProcessBuilder("bash", "-lc", command)
        .directory(context.workspace().root().toFile())
        .redirectErrorStream(false)
        .start();
    var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(context.timeout().toMillis());
    while (process.isAlive()) {
      if (context.cancelled().get()) {
        terminate(process);
        return ToolResult.failure(call.id(), "Command cancelled");
      }
      if (System.nanoTime() >= deadline) {
        terminate(process);
        return ToolResult.failure(call.id(), "Command timed out after " + context.timeout().toSeconds() + "s");
      }
      var remainingNanos = deadline - System.nanoTime();
      var waitMillis = Math.max(1, Math.min(POLL_MILLIS, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
      process.waitFor(waitMillis, TimeUnit.MILLISECONDS);
    }
    if (context.cancelled().get()) {
      return ToolResult.failure(call.id(), "Command cancelled");
    }
    var stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    var stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    return ToolResult.success(
        call.id(),
        Map.of("exitCode", process.exitValue(), "stdout", stdout, "stderr", stderr),
        "Command exited " + process.exitValue());
  }

  private static void terminate(Process process) throws InterruptedException {
    process.destroy();
    if (!process.waitFor(SHUTDOWN_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
      process.destroyForcibly();
      process.waitFor(SHUTDOWN_GRACE_MILLIS, TimeUnit.MILLISECONDS);
    }
  }
}
