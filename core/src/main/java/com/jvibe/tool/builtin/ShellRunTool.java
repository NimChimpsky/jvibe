package com.jvibe.tool.builtin;

import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ShellRunTool {
  private static final long POLL_MILLIS = 50;
  private static final long SHUTDOWN_GRACE_MILLIS = 1_000;
  private static final int MAX_CAPTURE_BYTES = 4 * 1024 * 1024;

  private ShellRunTool() {}

  public static RegisteredTool registered() {
    return new RegisteredTool(
        new ToolDefinition(
            "shell_run",
            "Run shell command",
            "Run a shell command in the workspace using JVM process APIs.",
            ToolSchemas.object(Map.of("command", ToolSchemas.string("Shell command to run from the workspace root.")), "command")),
        ShellRunTool::execute);
  }

  static ToolResult execute(ToolCall call, ToolExecutionContext context) throws Exception {
    var command = ToolArguments.string(call.arguments(), "command");
    var process = new ProcessBuilder(shellCommand(command))
        .directory(context.workspace().root().toFile())
        .redirectErrorStream(false)
        .start();
    try (var readers = Executors.newVirtualThreadPerTaskExecutor()) {
      var stdoutReader = readers.submit(() -> readLimited(process.getInputStream()));
      var stderrReader = readers.submit(() -> readLimited(process.getErrorStream()));
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
      var stdout = stdoutReader.get();
      var stderr = stderrReader.get();
      var payload = Map.<String, Object>of(
          "exitCode", process.exitValue(), "stdout", stdout, "stderr", stderr);
      var summary = "Command exited " + process.exitValue();
      return process.exitValue() == 0
          ? ToolResult.success(call.id(), payload, summary)
          : ToolResult.failure(call.id(), payload, summary);
    }
  }

  private static java.util.List<String> shellCommand(String command) {
    if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
      return java.util.List.of("powershell.exe", "-NoLogo", "-NonInteractive", "-Command", command);
    }
    return java.util.List.of("/bin/sh", "-lc", command);
  }

  private static String readLimited(InputStream input) throws java.io.IOException {
    var captured = new ByteArrayOutputStream();
    var buffer = new byte[8192];
    var truncated = false;
    int count;
    while ((count = input.read(buffer)) >= 0) {
      var remaining = MAX_CAPTURE_BYTES - captured.size();
      if (remaining > 0) {
        captured.write(buffer, 0, Math.min(remaining, count));
      }
      truncated |= count > remaining;
    }
    var text = captured.toString(StandardCharsets.UTF_8);
    return truncated ? text + "\n[output truncated at 4 MiB]" : text;
  }

  private static void terminate(Process process) throws InterruptedException {
    process.destroy();
    if (!process.waitFor(SHUTDOWN_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
      process.destroyForcibly();
      process.waitFor(SHUTDOWN_GRACE_MILLIS, TimeUnit.MILLISECONDS);
    }
  }
}
