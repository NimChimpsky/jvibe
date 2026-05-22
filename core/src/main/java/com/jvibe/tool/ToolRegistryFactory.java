package com.jvibe.tool;

import com.jvibe.tool.builtin.BuiltinToolSet;
import com.jvibe.tool.dynamic.DynamicToolRegistry;
import com.jvibe.workspace.Workspace;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class ToolRegistryFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(ToolRegistryFactory.class);
  private final BuiltinToolSet builtinTools;
  private final DynamicToolRegistry dynamicTools;

  public ToolRegistryFactory(BuiltinToolSet builtinTools, DynamicToolRegistry dynamicTools) {
    this.builtinTools = builtinTools;
    this.dynamicTools = dynamicTools;
  }

  public ToolRegistry create(Workspace workspace) {
    var registry = new ToolRegistry();
    builtinTools.tools().forEach(registry::register);
    try {
      dynamicTools.discoverBuildAndRegister(workspace, registry);
    } catch (Exception e) {
      LOGGER.warn("Dynamic tool discovery failed for {}", workspace.root(), e);
    }
    return registry;
  }
}
