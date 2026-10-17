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
  private final com.jvibe.git.GitService git = new com.jvibe.git.GitService(new com.jvibe.process.ProcessRunner());

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
        ToolExecutionContext.defaults(new Workspace(temp)), git);

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
        ToolExecutionContext.defaults(new Workspace(temp)), git);

    assertTrue(result.success());
    assertEquals("hello\n", Files.readString(temp.resolve("hello.txt")));
    assertEquals(true, result.payload().get("dryRun"));
  }

  @Test
  void rejectsMalformedPatch() throws Exception {
    var result = PatchApplyTool.execute(
        new ToolCall("1", "file_apply_patch", Map.of("patch", "not a patch")),
        ToolExecutionContext.defaults(new Workspace(temp)), git);

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
          ToolExecutionContext.defaults(new Workspace(temp)), git));

    assertTrue(error.getMessage().contains("escapes workspace"));
  }

  @Test
  void appliesPathsContainingSpacesAndGitEscapedCharacters() throws Exception {
    for (var name : java.util.List.of("two words.txt", "quoted\"name.txt")) {
      Files.writeString(temp.resolve(name), "hello\n");
      var header = name.contains("\"") ? "\"a/quoted\\\"name.txt\"" : "a/" + name;
      var target = name.contains("\"") ? "\"b/quoted\\\"name.txt\"" : "b/" + name;
      var patch = "--- " + header + "\n+++ " + target + "\n@@ -1 +1 @@\n-hello\n+updated\n";
      var result = PatchApplyTool.execute(new ToolCall("patch", "file_apply_patch", Map.of("patch", patch)),
          ToolExecutionContext.defaults(new Workspace(temp)), git);
      assertTrue(result.success(), result.summary());
      assertEquals(java.util.List.of(name), result.payload().get("files"));
      assertEquals("updated\n", Files.readString(temp.resolve(name)));
    }
  }

  @Test
  void rejectsSymlinkEscapeBeforeGitRuns() throws Exception {
    var root = Files.createDirectory(temp.resolve("workspace"));
    var outside = Files.createDirectory(temp.resolve("outside"));
    Files.writeString(outside.resolve("file.txt"), "hello\n");
    Files.createSymbolicLink(root.resolve("link"), outside);
    var patch = "--- a/link/file.txt\n+++ b/link/file.txt\n@@ -1 +1 @@\n-hello\n+changed\n";
    assertThrows(IllegalArgumentException.class, () -> PatchApplyTool.execute(
        new ToolCall("patch", "file_apply_patch", Map.of("patch", patch)),
        ToolExecutionContext.defaults(new Workspace(root)), git));
    assertEquals("hello\n", Files.readString(outside.resolve("file.txt")));
  }

  @Test
  void failedMultiFileDryRunLeavesAllFilesUnchanged() throws Exception {
    Files.writeString(temp.resolve("one.txt"), "hello\n");
    Files.writeString(temp.resolve("two.txt"), "different\n");
    var patch = "--- a/one.txt\n+++ b/one.txt\n@@ -1 +1 @@\n-hello\n+changed\n"
        + "--- a/two.txt\n+++ b/two.txt\n@@ -1 +1 @@\n-hello\n+changed\n";
    var result = PatchApplyTool.execute(new ToolCall("patch", "file_apply_patch", Map.of("patch", patch)),
        ToolExecutionContext.defaults(new Workspace(temp)), git);
    assertTrue(!result.success());
    assertEquals("hello\n", Files.readString(temp.resolve("one.txt")));
    assertEquals("different\n", Files.readString(temp.resolve("two.txt")));
  }
}
