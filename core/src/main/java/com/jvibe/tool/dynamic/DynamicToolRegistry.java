package com.jvibe.tool.dynamic;

import com.jvibe.config.JvibePaths;
import com.jvibe.git.GitService;
import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolRegistry;
import com.jvibe.tool.ToolResult;
import com.jvibe.workspace.Workspace;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.annotation.PreDestroy;

@Singleton
public final class DynamicToolRegistry {
  private static final Logger LOGGER = LoggerFactory.getLogger(DynamicToolRegistry.class);
  private final Path userToolsDir;
  private final ToolBuildService buildService;
  private final GitService gitService;
  private final Map<String, LoadedDynamicTool> loaded = new LinkedHashMap<>();

  @Inject
  public DynamicToolRegistry(JvibePaths paths, ToolBuildService buildService, GitService gitService) {
    this(paths.tools(), buildService, gitService);
  }

  DynamicToolRegistry(Path userToolsDir, ToolBuildService buildService) {
    this(userToolsDir, buildService, new GitService());
  }

  DynamicToolRegistry(Path userToolsDir, ToolBuildService buildService, GitService gitService) {
    this.userToolsDir = userToolsDir;
    this.buildService = buildService;
    this.gitService = gitService;
  }

  public synchronized Map<String, LoadedDynamicTool> loaded() {
    return Map.copyOf(loaded);
  }

  public void discoverBuildAndRegister(Workspace workspace, ToolRegistry targetRegistry) throws Exception {
    if (Files.exists(userToolsDir)) {
      discoverDirectory(userToolsDir, targetRegistry);
    }
    var projectTools = workspace.root().resolve(".jvibe").resolve("tools");
    if (Files.exists(projectTools)) {
      discoverDirectory(projectTools, targetRegistry);
    }
  }

  public synchronized LoadedDynamicTool buildLoadAndRegister(Path toolDir, ToolRegistry targetRegistry) throws Exception {
    var manifest = ToolManifest.parse(Files.readString(toolDir.resolve("tool.json")));
    if (!manifest.enabled()) {
      throw new IllegalStateException("Tool is disabled: " + manifest.id());
    }
    if (!manifest.approved()) {
      throw new IllegalStateException("Tool requires approval before build and execution: " + manifest.id());
    }
    var build = buildService.build(toolDir, manifest);
    if (!build.success()) {
      throw new IllegalStateException("Dynamic tool build failed: " + build.output());
    }
    var loader = new URLClassLoader(new java.net.URL[] {build.jar().toUri().toURL()}, ToolPlugin.class.getClassLoader());
    var type = Class.forName(manifest.entryClass(), true, loader);
    var plugin = (ToolPlugin) type.getDeclaredConstructor().newInstance();
    var loadedTool = new LoadedDynamicTool(manifest, build.jar(), loader, plugin);
    closeLoaded(loaded.put(manifest.id(), loadedTool));
    targetRegistry.register(asRegisteredTool(loadedTool, targetRegistry));
    return loadedTool;
  }

  public synchronized void unload(String id, ToolRegistry targetRegistry) {
    closeLoaded(loaded.remove(id));
    targetRegistry.unregister(id);
  }

  private void discoverDirectory(Path root, ToolRegistry targetRegistry) throws Exception {
    try (var stream = Files.list(root)) {
      for (var toolDir : stream.filter(Files::isDirectory).toList()) {
        if (Files.exists(toolDir.resolve("tool.json"))) {
          try {
            var manifest = ToolManifest.parse(Files.readString(toolDir.resolve("tool.json")));
            if (manifest.enabled() && manifest.approved()) {
              buildLoadAndRegister(toolDir, targetRegistry);
            }
          } catch (Exception e) {
            LOGGER.warn("Unable to load dynamic tool from {}", toolDir, e);
          }
        }
      }
    }
  }

  private static void closeLoaded(LoadedDynamicTool tool) {
    if (tool == null) {
      return;
    }
    try {
      tool.classLoader().close();
    } catch (java.io.IOException e) {
      LOGGER.warn("Unable to close classloader for dynamic tool {}", tool.manifest().id(), e);
    }
  }

  @PreDestroy
  public synchronized void close() {
    loaded.values().forEach(DynamicToolRegistry::closeLoaded);
    loaded.clear();
  }

  private RegisteredTool asRegisteredTool(LoadedDynamicTool loadedTool, ToolRegistry registry) {
    var definition = loadedTool.plugin().definition();
    if (!definition.id().equals(loadedTool.manifest().id())) {
      definition = new ToolDefinition(
          loadedTool.manifest().id(),
          definition.name(),
          definition.description(),
          definition.inputSchema());
    }
    var finalDefinition = definition;
    return new RegisteredTool(finalDefinition, (ToolCall call, ToolExecutionContext context) -> {
      var pluginContext = new ToolPluginContext(
          context.workspace(),
          registry,
          gitService,
          LOGGER,
          context.cancelled());
      return loadedTool.plugin().execute(call, pluginContext);
    });
  }
}
