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
  void readsOnlyAttachedReferencesNotEvenTmp() throws Exception {
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
    assertFalse(registry.execute(new ToolCall("file_read", Map.of("path", tmp.resolve("out.txt").toString())), context).success());
    assertFalse(registry.execute(new ToolCall("file_read", Map.of("path", outside.toString())), context).success());
  }

  @Test
  void cannotWriteEvenUnderTmp() throws Exception {
    var tmp = temp.resolve(".jvibe").resolve("tmp");
    Files.createDirectories(tmp);
    var registry = registry(tmp, List::of);
    var context = ToolExecutionContext.defaults(new Workspace(tmp));

    var written = registry.execute(new ToolCall("file_write", Map.of("path", "answer.txt", "content", "hello")), context);
    var escaped = registry.execute(new ToolCall("file_write", Map.of("path", temp.resolve("escape.txt").toString(), "content", "no")), context);

    assertFalse(written.success());
    assertFalse(Files.exists(tmp.resolve("answer.txt")));
    assertFalse(escaped.success());
  }

  private static com.jvibe.tool.ToolRegistry registry(Path tmp, java.util.function.Supplier<List<Path>> refs) {
    return FileToolSet.chat(refs);
  }
}
