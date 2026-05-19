package com.jvibe.tool.dynamic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolRegistry;
import com.jvibe.workspace.Workspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DynamicToolRegistryTest {
  @TempDir Path temp;

  @Test
  void compilesLoadsAndRegistersSimpleJavaTool() throws Exception {
    var toolDir = temp.resolve("user-tools").resolve("hello.tool");
    Files.createDirectories(toolDir.resolve("src").resolve("sample"));
    Files.writeString(toolDir.resolve("tool.json"), """
        {
          "id": "hello.tool",
          "name": "Hello Tool",
          "description": "Says hello",
          "version": "0.1.0",
          "pluginApiVersion": "1",
          "entryClass": "sample.HelloTool",
          "permissions": [],
          "dependencies": [],
          "enabled": true,
          "approved": true
        }
        """);
    Files.writeString(toolDir.resolve("src").resolve("sample").resolve("HelloTool.java"), """
        package sample;

        import com.jvibe.approval.ApprovalRequest;
        import com.jvibe.tool.ToolCall;
        import com.jvibe.tool.ToolDefinition;
        import com.jvibe.tool.ToolResult;
        import com.jvibe.tool.dynamic.ToolPlugin;
        import com.jvibe.tool.dynamic.ToolPluginContext;
        import java.util.Map;

        public final class HelloTool implements ToolPlugin {
          public ToolDefinition definition() {
            return new ToolDefinition("hello.tool", "Hello Tool", "Says hello", Map.of("type", "object"), ApprovalRequest.Risk.LOW);
          }

          public ToolResult execute(ToolCall call, ToolPluginContext context) {
            return ToolResult.success(call.id(), Map.of("message", "hello " + call.arguments().getOrDefault("name", "world")), "said hello");
          }
        }
        """);

    var registry = new ToolRegistry();
    var dynamic = new DynamicToolRegistry(temp.resolve("user-tools"), new ToolBuildService());
    dynamic.buildLoadAndRegister(toolDir, registry);

    assertTrue(registry.find("hello.tool").isPresent());
    var result = registry.execute(
        new ToolCall("hello.tool", Map.of("name", "jvibe")),
        ToolExecutionContext.defaults(new Workspace(temp)));
    assertTrue(result.success());
    assertEquals("hello jvibe", result.payload().get("message"));
  }
}
