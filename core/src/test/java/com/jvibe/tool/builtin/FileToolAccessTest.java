package com.jvibe.tool.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.workspace.Workspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileToolAccessTest {
  @TempDir
  Path temp;

  @Test
  void readsOnlyAttachedReferencesAndTmp() throws Exception {
    var attached = temp.resolve("attached.txt");
    var outside = temp.resolve("outside.txt");
    var tmp = temp.resolve("home").resolve(".jvibe").resolve("tmp");
    Files.createDirectories(tmp);
    Files.writeString(attached, "attached");
    Files.writeString(outside, "outside");
    Files.writeString(tmp.resolve("out.txt"), "output");
    var registry = registry(tmp, () -> List.of(attached));
    var context = ToolExecutionContext.defaults(new Workspace(tmp));

    assertTrue(registry.execute(new ToolCall("file_read", Map.of("path", attached.toString())), context).success());
    assertTrue(registry.execute(new ToolCall("file_read", Map.of("path", tmp.resolve("out.txt").toString())), context).success());
    assertFalse(registry.execute(new ToolCall("file_read", Map.of("path", outside.toString())), context).success());
  }

  @Test
  void writesOnlyUnderTmp() throws Exception {
    var tmp = temp.resolve(".jvibe").resolve("tmp");
    Files.createDirectories(tmp);
    var registry = registry(tmp, List::of);
    var context = ToolExecutionContext.defaults(new Workspace(tmp));

    var written = registry.execute(new ToolCall("file_write", Map.of("path", "answer.txt", "content", "hello")), context);
    var escaped = registry.execute(new ToolCall("file_write", Map.of("path", temp.resolve("escape.txt").toString(), "content", "no")), context);

    assertTrue(written.success());
    assertEquals("hello", Files.readString(tmp.resolve("answer.txt")));
    assertFalse(escaped.success());
  }

  @Test
  void defaultChatDoesNotExposeMutationToolsOutsideTmp() {
    var registry = registry(temp.resolve("tmp"), List::of);
    var ids = registry.definitions().stream().map(definition -> definition.id()).toList();

    assertEquals(List.of("file_list", "file_read", "file_write"), ids);
  }

  private static com.jvibe.tool.ToolRegistry registry(Path tmp, java.util.function.Supplier<List<Path>> refs) {
    var registry = new com.jvibe.tool.ToolRegistry();
    FileToolSet.defaultChat(tmp, refs).forEach(registry::register);
    return registry;
  }
}
