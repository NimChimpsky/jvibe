package com.jvibe.tool;

import com.jvibe.tool.builtin.BuiltinToolSet;
import com.jvibe.tool.builtin.FileToolSet;
import com.jvibe.tool.dynamic.DynamicToolRegistry;
import com.jvibe.tool.dynamic.DynamicToolManagementTools;
import com.jvibe.workspace.Workspace;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class ToolRegistryFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(ToolRegistryFactory.class);
  private final BuiltinToolSet builtinTools;
  private final DynamicToolRegistry dynamicTools;
  private final DynamicToolManagementTools dynamicToolManagement;

  public ToolRegistryFactory(
      BuiltinToolSet builtinTools,
      DynamicToolRegistry dynamicTools,
      DynamicToolManagementTools dynamicToolManagement) {
    this.builtinTools = builtinTools;
    this.dynamicTools = dynamicTools;
    this.dynamicToolManagement = dynamicToolManagement;
  }

  public ToolRegistry create(Workspace workspace) {
    var registry = new ToolRegistry();
    builtinTools.tools().forEach(registry::register);
    dynamicToolManagement.tools(workspace, registry).forEach(registry::register);
    try {
      dynamicTools.discoverBuildAndRegister(workspace, registry);
    } catch (Exception e) {
      LOGGER.warn("Dynamic tool discovery failed for {}", workspace.root(), e);
    }
    return registry;
  }

  public ToolRegistry createDefaultChat(Supplier<List<Path>> references) {
    var registry = new ToolRegistry();
    FileToolSet.defaultChat(references).forEach(registry::register);
    return registry;
  }

}
