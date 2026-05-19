package com.jvibe.tool.dynamic;

import com.jvibe.git.GitService;
import com.jvibe.tool.ToolRegistry;
import com.jvibe.workspace.Workspace;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

public record ToolPluginContext(
    Workspace workspace,
    ToolRegistry toolRegistry,
    GitService gitService,
    Logger logger,
    AtomicBoolean cancelled) {
  public void ensureActive() {
    if (cancelled.get()) {
      throw new IllegalStateException("Dynamic tool execution cancelled");
    }
  }
}
