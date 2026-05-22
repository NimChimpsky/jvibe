package com.jvibe.tool.builtin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.tool.ToolDefinition;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BuiltinToolsTest {
  @Test
  void exposesMvpToolIds() {
    var ids = BuiltinTools.registry().definitions().stream().map(definition -> definition.id()).collect(Collectors.toSet());
    assertTrue(ids.containsAll(Set.of(
        "shell_run",
        "file_read",
        "file_write",
        "file_patch",
        "file_apply_patch",
        "file_list",
        "git_status",
        "git_diff",
        "git_commit",
        "git_push",
        "tool_list",
        "tool_inspect",
        "tool_create",
        "tool_build",
        "tool_reload",
        "tool_disable",
        "tool_delete",
        "tool_approve")));
    assertTrue(ids.stream().allMatch(ToolDefinition::isValidId));
  }

  @Test
  void exposesRequiredArgumentsInSchemas() {
    var definitions = BuiltinTools.registry().definitions().stream()
        .collect(Collectors.toMap(ToolDefinition::id, definition -> definition));

    assertTrue(required(definitions.get("shell_run")).contains("command"));
    assertTrue(required(definitions.get("file_read")).contains("path"));
    assertTrue(required(definitions.get("file_write")).containsAll(List.of("path", "content")));
    assertTrue(required(definitions.get("file_apply_patch")).contains("patch"));
    assertTrue(required(definitions.get("git_commit")).contains("message"));
  }

  @SuppressWarnings("unchecked")
  private static List<String> required(ToolDefinition definition) {
    return (List<String>) definition.inputSchema().getOrDefault("required", List.of());
  }
}
