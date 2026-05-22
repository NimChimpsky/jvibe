package com.jvibe.tool.builtin;

import com.jvibe.tool.RegisteredTool;
import java.util.List;

public record BuiltinToolSet(List<RegisteredTool> tools) {
  public BuiltinToolSet {
    tools = List.copyOf(tools);
  }
}
