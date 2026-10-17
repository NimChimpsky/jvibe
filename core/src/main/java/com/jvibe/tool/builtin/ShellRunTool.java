package com.jvibe.tool.builtin;

import com.jvibe.process.ProcessResult;
import com.jvibe.process.ProcessRunner;
import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolArguments;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;

public final class ShellRunTool {
  private ShellRunTool() {}

  public static RegisteredTool registered(ProcessRunner processes) {
    return RegisteredTool.changing("shell_run", (call, context) -> execute(call, context, processes));
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context, ProcessRunner processes) throws Exception {
    var command = ToolArguments.string(call.arguments(), "command");
    if (command.isBlank()) return ToolResult.failure(call.id(), "Missing argument: command");
    var result = processes.run(shellCommand(command), context.workspace().root(), new byte[0],
        context.timeout(), context.cancelled());
    if (result.status() == ProcessResult.Status.CANCELLED) {
      return ToolResult.failure(call.id(), "Command cancelled");
    }
    if (result.status() == ProcessResult.Status.TIMED_OUT) {
      return ToolResult.failure(call.id(), "Command timed out after " + context.timeout().toSeconds() + "s");
    }
    return ToolResult.exit(call.id(), result.exitCode(), result.stdout(), result.stderr(),
        "Command exited " + result.exitCode());
  }

  private static java.util.List<String> shellCommand(String command) {
    if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
      return java.util.List.of("powershell.exe", "-NoLogo", "-NonInteractive", "-Command", command);
    }
    return java.util.List.of("/bin/sh", "-lc", command);
  }

}
