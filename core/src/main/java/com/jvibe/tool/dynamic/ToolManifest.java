package com.jvibe.tool.dynamic;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
  private static final Pattern STRING_ARRAY = Pattern.compile("\"%s\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
  private static final Pattern STRING_FIELD = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"", Pattern.DOTALL);
  private static final Pattern BOOLEAN_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);

  public static ToolManifest parse(String json) {
    return new ToolManifest(
        requiredString(json, "id"),
        requiredString(json, "name"),
        requiredString(json, "description"),
        string(json, "version", "0.1.0"),
        string(json, "pluginApiVersion", "1"),
        requiredString(json, "entryClass"),
        Map.of("type", "object"),
        stringArray(json, "permissions"),
        stringArray(json, "dependencies"),
        bool(json, "enabled", true),
        bool(json, "approved", false));
  }

  private static String requiredString(String json, String field) {
    var value = string(json, field, null);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing required tool manifest field: " + field);
    }
    return value;
  }

  private static String string(String json, String field, String fallback) {
    var matcher = Pattern.compile(STRING_FIELD.pattern().formatted(Pattern.quote(field)), Pattern.DOTALL).matcher(json);
    return matcher.find() ? matcher.group(1) : fallback;
  }

  private static boolean bool(String json, String field, boolean fallback) {
    var matcher = Pattern.compile(BOOLEAN_FIELD.pattern().formatted(Pattern.quote(field)), Pattern.CASE_INSENSITIVE).matcher(json);
    return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : fallback;
  }

  private static List<String> stringArray(String json, String field) {
    var matcher = Pattern.compile(STRING_ARRAY.pattern().formatted(Pattern.quote(field)), Pattern.DOTALL).matcher(json);
    if (!matcher.find()) {
      return List.of();
    }
    var body = matcher.group(1);
    var valueMatcher = Pattern.compile("\"([^\"]+)\"").matcher(body);
    var values = new java.util.ArrayList<String>();
    while (valueMatcher.find()) {
      values.add(valueMatcher.group(1));
    }
    return List.copyOf(values);
  }
}
