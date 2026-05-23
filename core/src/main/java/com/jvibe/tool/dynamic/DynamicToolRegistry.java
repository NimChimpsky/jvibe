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
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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

  public DynamicToolRegistry(Path userToolsDir, ToolBuildService buildService) {
    this(userToolsDir, buildService, new GitService());
  }

  DynamicToolRegistry(Path userToolsDir, ToolBuildService buildService, GitService gitService) {
    this.userToolsDir = userToolsDir;
    this.buildService = buildService;
    this.gitService = gitService;
  }

  public Map<String, LoadedDynamicTool> loaded() {
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

  public LoadedDynamicTool buildLoadAndRegister(Path toolDir, ToolRegistry targetRegistry) throws Exception {
    var manifest = ToolManifest.parse(Files.readString(toolDir.resolve("tool.json")));
    if (!manifest.enabled()) {
      throw new IllegalStateException("Tool is disabled: " + manifest.id());
    }
    var build = buildService.build(toolDir, manifest);
    if (!build.success()) {
      throw new IllegalStateException("Dynamic tool build failed: " + build.output());
    }
    var loader = new URLClassLoader(new java.net.URL[] {build.jar().toUri().toURL()}, ToolPlugin.class.getClassLoader());
    var type = Class.forName(manifest.entryClass(), true, loader);
    var plugin = (ToolPlugin) type.getDeclaredConstructor().newInstance();
    var loadedTool = new LoadedDynamicTool(manifest, build.jar(), loader, plugin);
    loaded.put(manifest.id(), loadedTool);
    targetRegistry.register(asRegisteredTool(loadedTool, targetRegistry));
    return loadedTool;
  }

  private void discoverDirectory(Path root, ToolRegistry targetRegistry) throws Exception {
    try (var stream = Files.list(root)) {
      for (var toolDir : stream.filter(Files::isDirectory).toList()) {
        if (Files.exists(toolDir.resolve("tool.json"))) {
          buildLoadAndRegister(toolDir, targetRegistry);
        }
      }
    }
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
          new AtomicBoolean(context.cancelled().get()));
      return loadedTool.plugin().execute(call, pluginContext);
    });
  }
}
