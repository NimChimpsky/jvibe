package com.jvibe.tool.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.workspace.Workspace;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShellRunToolTest {
  @TempDir Path temp;

  @Test
  void runsCommandAndCapturesOutput() throws Exception {
    var result = ShellRunTool.execute(
        call("printf hello"),
        new ToolExecutionContext(new Workspace(temp), Duration.ofSeconds(5), new AtomicBoolean(false)));

    assertTrue(result.success());
    assertEquals("hello", result.payload().get("stdout"));
    assertEquals(0, result.payload().get("exitCode"));
  }

  @Test
  void timeoutKillsLongRunningCommand() throws Exception {
    var started = System.nanoTime();
    var result = ShellRunTool.execute(
        call("sleep 5"),
        new ToolExecutionContext(new Workspace(temp), Duration.ofMillis(100), new AtomicBoolean(false)));
    var elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

    assertFalse(result.success());
    assertTrue(result.summary().contains("timed out"));
    assertTrue(elapsedMillis < 3_000, "timeout took " + elapsedMillis + "ms");
  }

  @Test
  void cancellationKillsLongRunningCommandPromptly() throws Exception {
    var cancelled = new AtomicBoolean(false);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var future = executor.submit(() -> ShellRunTool.execute(
          call("sleep 30"),
          new ToolExecutionContext(new Workspace(temp), Duration.ofSeconds(30), cancelled)));

      Thread.sleep(150);
      cancelled.set(true);
      var result = future.get(2, TimeUnit.SECONDS);

      assertFalse(result.success());
      assertTrue(result.summary().contains("cancelled"));
    } finally {
      executor.shutdownNow();
    }
  }

  private static ToolCall call(String command) {
    return new ToolCall("call_1", "shell_run", Map.of("command", command));
  }
}
