package com.jvibe.tool.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.git.GitService;
import com.jvibe.process.ProcessRunner;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.workspace.Workspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileToolSetTest {
  @TempDir Path root;

  /** Must match the tool names the server defines for the Build and Chat modes. */
  private static final List<String> BUILD_TOOLS = List.of("file_list", "file_read", "file_write", "file_patch",
      "file_apply_patch", "shell_run", "git_status", "git_diff", "git_commit", "git_push");

  @Test
  void buildRegistryImplementsEveryServerDefinedTool() {
    var processes = new ProcessRunner();
    var registry = new FileToolSet(new GitService(processes), processes).build();
    for (var name : BUILD_TOOLS) {
      assertTrue(registry.find(name).isPresent(), name);
    }
    assertFalse(registry.find("file_read").orElseThrow().changesState());
    assertTrue(registry.find("file_write").orElseThrow().changesState());
  }

  @Test
  void chatRegistryIsReadOnly() {
    var registry = FileToolSet.chat(List::of);
    assertTrue(registry.find("file_read").isPresent());
    assertTrue(registry.find("file_list").isPresent());
    assertTrue(registry.find("file_write").isEmpty());
  }

  @Test
  void filePatchReplacesExactlyOneOccurrence() throws Exception {
    var file = root.resolve("App.java");
    Files.writeString(file, "a = 1;\nb = 1;\n");
    var context = ToolExecutionContext.defaults(new Workspace(root));

    var ambiguous = FilePatchTool.execute(new ToolCall("1", "file_patch",
        Map.of("path", "App.java", "search", "= 1;", "replacement", "= 2;")), context);
    assertFalse(ambiguous.success());
    assertTrue(ambiguous.summary().contains("more than once"));

    var patched = FilePatchTool.execute(new ToolCall("2", "file_patch",
        Map.of("path", "App.java", "search", "b = 1;", "replacement", "b = 2;")), context);
    assertTrue(patched.success());
    assertEquals("a = 1;\nb = 2;\n", Files.readString(file));
  }
}
