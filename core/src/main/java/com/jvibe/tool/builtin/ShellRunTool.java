package com.jvibe.tool.builtin;

import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import java.util.Map;

public final class ShellRunTool {
  private ShellRunTool() {}

  public static RegisteredTool registered(com.jvibe.process.ProcessRunner processes) {
    return new RegisteredTool(
        new ToolDefinition(
            "shell_run",
            "Run shell command",
            "Run a shell command in the workspace using JVM process APIs.",
            ToolSchemas.object(Map.of("command", ToolSchemas.string("Shell command to run from the workspace root.")), "command")),
        (call, context) -> execute(call, context, processes));
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context,
      com.jvibe.process.ProcessRunner processes) throws Exception {
    var command = ToolArguments.string(call.arguments(), "command");
    if (command.isBlank()) return ToolResult.failure(call.id(), "Missing argument: command");
    var result = processes.run(shellCommand(command), context.workspace().root(), new byte[0],
        context.timeout(), context.cancelled());
    if (result.status() == com.jvibe.process.ProcessResult.Status.CANCELLED) {
      return ToolResult.failure(call.id(), "Command cancelled");
    }
    if (result.status() == com.jvibe.process.ProcessResult.Status.TIMED_OUT) {
      return ToolResult.failure(call.id(), "Command timed out after " + context.timeout().toSeconds() + "s");
    }
    var payload = Map.<String, Object>of(
        "exitCode", result.exitCode(), "stdout", result.stdout(), "stderr", result.stderr());
    var summary = "Command exited " + result.exitCode();
    return result.exitCode() == 0 ? ToolResult.success(call.id(), payload, summary)
        : ToolResult.failure(call.id(), payload, summary);
  }

  private static java.util.List<String> shellCommand(String command) {
    if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
      return java.util.List.of("powershell.exe", "-NoLogo", "-NonInteractive", "-Command", command);
    }
    return java.util.List.of("/bin/sh", "-lc", command);
  }

}
