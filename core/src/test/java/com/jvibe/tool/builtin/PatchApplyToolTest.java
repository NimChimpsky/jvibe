package com.jvibe.tool.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.workspace.Workspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PatchApplyToolTest {
  @TempDir Path temp;

  @Test
  void appliesValidUnifiedPatch() throws Exception {
    Files.writeString(temp.resolve("hello.txt"), "hello\n");
    var patch = """
        diff --git a/hello.txt b/hello.txt
        --- a/hello.txt
        +++ b/hello.txt
        @@ -1 +1 @@
        -hello
        +goodbye
        """;

    var result = PatchApplyTool.execute(
        new ToolCall("1", "file_apply_patch", Map.of("patch", patch)),
        ToolExecutionContext.defaults(new Workspace(temp)));

    assertTrue(result.success());
    assertEquals("goodbye\n", Files.readString(temp.resolve("hello.txt")));
    assertEquals(1, result.payload().get("hunks"));
  }

  @Test
  void dryRunLeavesFileUnchanged() throws Exception {
    Files.writeString(temp.resolve("hello.txt"), "hello\n");
    var patch = """
        diff --git a/hello.txt b/hello.txt
        --- a/hello.txt
        +++ b/hello.txt
        @@ -1 +1 @@
        -hello
        +goodbye
        """;

    var result = PatchApplyTool.execute(
        new ToolCall("1", "file_apply_patch", Map.of("patch", patch, "dryRun", true)),
        ToolExecutionContext.defaults(new Workspace(temp)));

    assertTrue(result.success());
    assertEquals("hello\n", Files.readString(temp.resolve("hello.txt")));
    assertEquals(true, result.payload().get("dryRun"));
  }

  @Test
  void rejectsMalformedPatch() throws Exception {
    var result = PatchApplyTool.execute(
        new ToolCall("1", "file_apply_patch", Map.of("patch", "not a patch")),
        ToolExecutionContext.defaults(new Workspace(temp)));

    assertTrue(!result.success());
    assertTrue(result.summary().contains("unified diff"));
  }

  @Test
  void rejectsPathEscape() {
    var patch = """
        diff --git a/../outside.txt b/../outside.txt
        --- a/../outside.txt
        +++ b/../outside.txt
        @@ -1 +1 @@
        -a
        +b
        """;

    var error = assertThrows(IllegalArgumentException.class, () -> PatchApplyTool.execute(
          new ToolCall("1", "file_apply_patch", Map.of("patch", patch)),
          ToolExecutionContext.defaults(new Workspace(temp))));

    assertTrue(error.getMessage().contains("escapes workspace"));
  }
}
