package com.jvibe.tool.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.workspace.Workspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileReadListToolTest {
  @TempDir Path root;

  @Test
  void workspaceRootListsChildrenRatherThanOnlyTheRootPath() throws Exception {
    Files.writeString(root.resolve("build.gradle"), "plugins {}\n");
    Files.createDirectory(root.resolve("src"));

    var result = FileListTool.execute(
        new ToolCall("call", "file_list", Map.of("path", ".")),
        ToolExecutionContext.defaults(new Workspace(root)),
        FileToolAccess.workspace());

    assertEquals(java.util.List.of("build.gradle", "src"), result.payload().get("files"));
  }

  @Test
  void fileReadSupportsBoundedLinePaging() throws Exception {
    Files.writeString(root.resolve("sample.txt"), "one\ntwo\nthree\nfour\n");

    var result = FileReadTool.execute(
        new ToolCall("call", "file_read", Map.of("path", "sample.txt", "startLine", 2, "maxLines", 2)),
        ToolExecutionContext.defaults(new Workspace(root)),
        FileToolAccess.workspace());

    assertEquals("two\nthree", result.payload().get("content"));
    assertEquals(4, result.payload().get("totalLines"));
    assertTrue((Boolean) result.payload().get("truncated"));
  }
}
