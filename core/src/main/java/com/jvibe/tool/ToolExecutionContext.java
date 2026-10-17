package com.jvibe.tool;

import com.jvibe.workspace.Workspace;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public record ToolExecutionContext(Workspace workspace, Duration timeout, AtomicBoolean cancelled) {
  public static ToolExecutionContext defaults(Workspace workspace) {
    return new ToolExecutionContext(workspace, Duration.ofSeconds(120), new AtomicBoolean(false));
  }

  public void ensureActive() {
    if (cancelled.get() || Thread.currentThread().isInterrupted()) {
      throw new IllegalStateException("Tool execution cancelled");
    }
  }
}
