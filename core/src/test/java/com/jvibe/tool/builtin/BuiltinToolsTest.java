package com.jvibe.tool.builtin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BuiltinToolsTest {
  @Test
  void exposesMvpToolIds() {
    var ids = BuiltinTools.registry().definitions().stream().map(definition -> definition.id()).collect(Collectors.toSet());
    assertTrue(ids.containsAll(Set.of(
        "shell.run",
        "file.read",
        "file.write",
        "file.patch",
        "file.list",
        "git.status",
        "git.diff",
        "git.commit",
        "git.push",
        "tool.list",
        "tool.inspect",
        "tool.create",
        "tool.build",
        "tool.reload",
        "tool.disable",
        "tool.delete",
        "tool.approve")));
  }
}
