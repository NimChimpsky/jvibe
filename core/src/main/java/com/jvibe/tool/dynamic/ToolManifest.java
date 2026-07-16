package com.jvibe.tool.dynamic;

import com.jvibe.tool.ToolDefinition;
import io.avaje.jsonb.Json;
import io.avaje.jsonb.Jsonb;
import java.util.List;
import java.util.Map;

@Json
public record ToolManifest(
    String id,
    String name,
    String description,
    String version,
    String pluginApiVersion,
    String entryClass,
    Map<String, Object> inputSchema,
    List<String> permissions,
    List<String> dependencies,
    boolean enabled,
    boolean approved) {
  public ToolManifest {
    version = version == null || version.isBlank() ? "0.1.0" : version;
    pluginApiVersion = pluginApiVersion == null || pluginApiVersion.isBlank() ? "1" : pluginApiVersion;
    inputSchema = inputSchema == null ? Map.of("type", "object") : Map.copyOf(inputSchema);
    permissions = permissions == null ? List.of() : List.copyOf(permissions);
    dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
  }

  public static ToolManifest parse(String json) {
    var manifest = Jsonb.instance().type(ToolManifest.class).fromJson(json);
    manifest.validate();
    return manifest;
  }

  public String toJson() {
    return Jsonb.instance().toJsonPretty(this);
  }

  public ToolManifest withEnabled(boolean value) {
    return new ToolManifest(
        id, name, description, version, pluginApiVersion, entryClass, inputSchema,
        permissions, dependencies, value, approved);
  }

  public ToolManifest withApproved(boolean value) {
    return new ToolManifest(
        id, name, description, version, pluginApiVersion, entryClass, inputSchema,
        permissions, dependencies, enabled, value);
  }

  private void validate() {
    require(id, "id");
    require(name, "name");
    require(description, "description");
    require(entryClass, "entryClass");
    if (!ToolDefinition.isValidId(id)) {
      throw new IllegalArgumentException("Tool manifest id must match ^[a-zA-Z0-9_-]+$: " + id);
    }
  }

  private static void require(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing required tool manifest field: " + field);
    }
  }
}
