package com.jvibe.tool.dynamic;

import com.jvibe.config.JvibePaths;
import com.jvibe.config.AtomicFiles;
import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolRegistry;
import com.jvibe.tool.ToolResult;
import com.jvibe.workspace.Workspace;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Creates the model-facing lifecycle tools for dynamic Java plugins. */
@Singleton
public final class DynamicToolManagementTools {
  private final Path userTools;
  private final DynamicToolRegistry dynamicTools;

  public DynamicToolManagementTools(JvibePaths paths, DynamicToolRegistry dynamicTools) {
    this.userTools = paths.tools();
    this.dynamicTools = dynamicTools;
  }

  public List<RegisteredTool> tools(Workspace workspace, ToolRegistry registry) {
    return List.of(
        tool("tool_list", "List dynamic tools", "List project and user dynamic Java tools.", object(),
            (call, context) -> list(call.id(), workspace)),
        tool("tool_inspect", "Inspect dynamic tool", "Inspect a dynamic tool manifest and source files.", idAndScopeSchema(),
            (call, context) -> inspect(call.id(), workspace, call.arguments())),
        tool("tool_create", "Create dynamic tool", "Create a Java tool bundle scaffold. It requires user approval before build.", createSchema(),
            (call, context) -> create(call.id(), workspace, call.arguments())),
        tool("tool_build", "Build dynamic tool", "Build and load an approved dynamic Java tool in this session.", idAndScopeSchema(),
            (call, context) -> build(call.id(), workspace, registry, call.arguments())),
        tool("tool_reload", "Reload dynamic tool", "Rebuild and hot-reload an approved dynamic Java tool.", idAndScopeSchema(),
            (call, context) -> build(call.id(), workspace, registry, call.arguments())),
        tool("tool_disable", "Disable dynamic tool", "Disable and unload a dynamic Java tool.", idAndScopeSchema(),
            (call, context) -> disable(call.id(), workspace, registry, call.arguments())),
        tool("tool_delete", "Delete dynamic tool", "Delete and unload a dynamic Java tool bundle.", idAndScopeSchema(),
            (call, context) -> delete(call.id(), workspace, registry, call.arguments())));
  }

  public ToolResult approve(String callId, Workspace workspace, ToolRegistry registry, String id, String scope) {
    try {
      var directory = resolve(workspace, id, scope, true);
      var manifest = manifest(directory).withApproved(true).withEnabled(true);
      writeManifest(directory, manifest);
      var loaded = dynamicTools.buildLoadAndRegister(directory, registry);
      return ToolResult.success(
          callId,
          Map.of("tool", manifest.id(), "version", manifest.version(), "scope", scope(directory, workspace)),
          "Approved and loaded dynamic tool " + loaded.manifest().id());
    } catch (Exception e) {
      return ToolResult.failure(callId, message(e));
    }
  }

  public ToolResult reload(String callId, Workspace workspace, ToolRegistry registry, String id, String scope) {
    return build(callId, workspace, registry, Map.of("id", id, "scope", scope));
  }

  public ToolResult disable(String callId, Workspace workspace, ToolRegistry registry, String id, String scope) {
    return disable(callId, workspace, registry, Map.of("id", id, "scope", scope));
  }

  public ToolResult delete(String callId, Workspace workspace, ToolRegistry registry, String id, String scope) {
    return delete(callId, workspace, registry, Map.of("id", id, "scope", scope));
  }

  public List<ToolInfo> discovered(Workspace workspace) {
    var found = new LinkedHashMap<String, ToolInfo>();
    discover(userTools, "user", found);
    discover(projectTools(workspace), "project", found);
    return found.values().stream()
        .sorted(Comparator.comparing(ToolInfo::scope).thenComparing(ToolInfo::id))
        .toList();
  }

  private ToolResult list(String callId, Workspace workspace) {
    var tools = discovered(workspace);
    return ToolResult.success(
        callId,
        Map.of("tools", tools.stream().map(ToolInfo::asMap).toList()),
        tools.isEmpty() ? "No dynamic tools found" : "Found " + tools.size() + " dynamic tools");
  }

  private ToolResult inspect(String callId, Workspace workspace, Map<String, Object> arguments) {
    try {
      var directory = resolve(workspace, id(arguments), scope(arguments), true);
      var files = new ArrayList<String>();
      try (var stream = Files.walk(directory)) {
        stream.filter(Files::isRegularFile)
            .map(directory::relativize)
            .map(Path::toString)
            .sorted()
            .forEach(files::add);
      }
      return ToolResult.success(
          callId,
          Map.of("directory", directory.toString(), "manifest", manifest(directory), "files", files),
          "Inspected dynamic tool " + id(arguments));
    } catch (Exception e) {
      return ToolResult.failure(callId, message(e));
    }
  }

  private ToolResult create(String callId, Workspace workspace, Map<String, Object> arguments) {
    try {
      var id = id(arguments);
      var targetScope = scope(arguments);
      var directory = resolve(workspace, id, targetScope, false);
      if (Files.exists(directory)) {
        return ToolResult.failure(callId, "Dynamic tool already exists: " + directory);
      }
      var name = text(arguments, "name", id);
      var description = text(arguments, "description", "Dynamic Java tool " + name);
      var packageName = "com.jvibe.tools." + id.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
      var entryClass = packageName + ".GeneratedTool";
      var manifest = new ToolManifest(
          id,
          name,
          description,
          "0.1.0",
          "1",
          entryClass,
          object(),
          List.of(),
          List.of(),
          true,
          false);
      var source = directory.resolve("src").resolve(packageName.replace('.', '/')).resolve("GeneratedTool.java");
      Files.createDirectories(source.getParent());
      writeManifest(directory, manifest);
      AtomicFiles.writeString(source, source(packageName, id, name, description));
      return ToolResult.success(
          callId,
          Map.of("directory", directory.toString(), "manifest", directory.resolve("tool.json").toString(), "source", source.toString()),
          "Created dynamic tool " + id + "; user approval is required before build");
    } catch (Exception e) {
      return ToolResult.failure(callId, message(e));
    }
  }

  private ToolResult build(
      String callId,
      Workspace workspace,
      ToolRegistry registry,
      Map<String, Object> arguments) {
    try {
      var directory = resolve(workspace, id(arguments), scope(arguments), true);
      var loaded = dynamicTools.buildLoadAndRegister(directory, registry);
      return ToolResult.success(
          callId,
          Map.of("tool", loaded.manifest().id(), "jar", loaded.jar().toString()),
          "Built and loaded dynamic tool " + loaded.manifest().id());
    } catch (Exception e) {
      return ToolResult.failure(callId, message(e));
    }
  }

  private ToolResult disable(
      String callId,
      Workspace workspace,
      ToolRegistry registry,
      Map<String, Object> arguments) {
    try {
      var directory = resolve(workspace, id(arguments), scope(arguments), true);
      var manifest = manifest(directory).withEnabled(false);
      writeManifest(directory, manifest);
      dynamicTools.unload(manifest.id(), registry);
      return ToolResult.success(callId, Map.of("tool", manifest.id()), "Disabled dynamic tool " + manifest.id());
    } catch (Exception e) {
      return ToolResult.failure(callId, message(e));
    }
  }

  private ToolResult delete(
      String callId,
      Workspace workspace,
      ToolRegistry registry,
      Map<String, Object> arguments) {
    try {
      var directory = resolve(workspace, id(arguments), scope(arguments), true);
      var manifest = manifest(directory);
      dynamicTools.unload(manifest.id(), registry);
      try (var stream = Files.walk(directory)) {
        for (var path : stream.sorted(Comparator.reverseOrder()).toList()) {
          Files.delete(path);
        }
      }
      return ToolResult.success(callId, Map.of("tool", manifest.id()), "Deleted dynamic tool " + manifest.id());
    } catch (Exception e) {
      return ToolResult.failure(callId, message(e));
    }
  }

  private void discover(Path root, String scope, Map<String, ToolInfo> found) {
    if (Files.notExists(root)) {
      return;
    }
    try (var stream = Files.list(root)) {
      for (var directory : stream.filter(Files::isDirectory).toList()) {
        try {
          var manifest = manifest(directory);
          found.put(scope + ":" + manifest.id(), new ToolInfo(
              manifest.id(), manifest.name(), manifest.version(), scope,
              manifest.enabled(), manifest.approved(), dynamicTools.loaded().containsKey(manifest.id()), directory));
        } catch (RuntimeException | IOException ignored) {
          // Invalid bundles are omitted from model-facing discovery and remain inspectable on disk.
        }
      }
    } catch (IOException ignored) {
      // A missing or inaccessible tool scope behaves like an empty scope.
    }
  }

  private Path resolve(Workspace workspace, String id, String scope, boolean mustExist) {
    if (!ToolDefinition.isValidId(id)) {
      throw new IllegalArgumentException("Tool id must match ^[a-zA-Z0-9_-]+$: " + id);
    }
    Path directory;
    if ("user".equalsIgnoreCase(scope)) {
      directory = userTools.resolve(id).normalize();
    } else if ("project".equalsIgnoreCase(scope) || scope.isBlank()) {
      directory = projectTools(workspace).resolve(id).normalize();
    } else {
      throw new IllegalArgumentException("Tool scope must be project or user");
    }
    if (mustExist && Files.notExists(directory.resolve("tool.json"))) {
      throw new IllegalArgumentException("Dynamic tool not found: " + id + " (" + scope + ")");
    }
    return directory;
  }

  private static Path projectTools(Workspace workspace) {
    return workspace.root().resolve(".jvibe").resolve("tools");
  }

  private static ToolManifest manifest(Path directory) throws IOException {
    return ToolManifest.parse(Files.readString(directory.resolve("tool.json")));
  }

  private static void writeManifest(Path directory, ToolManifest manifest) throws IOException {
    Files.createDirectories(directory);
    AtomicFiles.writeString(directory.resolve("tool.json"), manifest.toJson());
  }

  private static RegisteredTool tool(
      String id,
      String name,
      String description,
      Map<String, Object> schema,
      com.jvibe.tool.ToolExecutor executor) {
    return new RegisteredTool(new ToolDefinition(id, name, description, schema), executor);
  }

  private static String id(Map<String, Object> arguments) {
    var value = arguments.get("id");
    if (value == null || value.toString().isBlank()) {
      throw new IllegalArgumentException("Missing argument: id");
    }
    return value.toString();
  }

  private static String scope(Map<String, Object> arguments) {
    return text(arguments, "scope", "project");
  }

  private static String scope(Path directory, Workspace workspace) {
    return directory.startsWith(projectTools(workspace)) ? "project" : "user";
  }

  private static String text(Map<String, Object> arguments, String key, String fallback) {
    var value = arguments.get(key);
    return value == null || value.toString().isBlank() ? fallback : value.toString();
  }

  private static Map<String, Object> object() {
    return Map.of("type", "object", "properties", Map.of(), "additionalProperties", false);
  }

  private static Map<String, Object> idAndScopeSchema() {
    return schema(Map.of(
        "id", string("Dynamic tool id"),
        "scope", Map.of("type", "string", "enum", List.of("project", "user"))), "id");
  }

  private static Map<String, Object> createSchema() {
    return schema(Map.of(
        "id", string("Provider-safe dynamic tool id"),
        "name", string("Human-readable tool name"),
        "description", string("Tool purpose and expected behaviour"),
        "scope", Map.of("type", "string", "enum", List.of("project", "user"))), "id");
  }

  private static Map<String, Object> schema(Map<String, Object> properties, String... required) {
    var schema = new LinkedHashMap<String, Object>();
    schema.put("type", "object");
    schema.put("properties", properties);
    schema.put("required", List.of(required));
    schema.put("additionalProperties", false);
    return schema;
  }

  private static Map<String, Object> string(String description) {
    return Map.of("type", "string", "description", description);
  }

  private static String source(String packageName, String id, String name, String description) {
    return """
        package %s;

        import com.jvibe.tool.ToolCall;
        import com.jvibe.tool.ToolDefinition;
        import com.jvibe.tool.ToolResult;
        import com.jvibe.tool.dynamic.ToolPlugin;
        import com.jvibe.tool.dynamic.ToolPluginContext;
        import java.util.Map;

        public final class GeneratedTool implements ToolPlugin {
          @Override
          public ToolDefinition definition() {
            return new ToolDefinition("%s", "%s", "%s", Map.of("type", "object"));
          }

          @Override
          public ToolResult execute(ToolCall call, ToolPluginContext context) {
            return ToolResult.success(call.id(), Map.of("arguments", call.arguments()), "Executed %s");
          }
        }
        """.formatted(packageName, java(id), java(name), java(description), java(name));
  }

  private static String java(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }

  private static String message(Exception exception) {
    return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
  }

  public record ToolInfo(
      String id,
      String name,
      String version,
      String scope,
      boolean enabled,
      boolean approved,
      boolean loaded,
      Path directory) {
    Map<String, Object> asMap() {
      return Map.of(
          "id", id,
          "name", name,
          "version", version,
          "scope", scope,
          "enabled", enabled,
          "approved", approved,
          "loaded", loaded,
          "directory", directory.toString());
    }
  }
}
