package com.jvibe.tool;

import com.jvibe.approval.ApprovalRequest;
import java.util.Map;
import java.util.regex.Pattern;

public record ToolDefinition(
    String id,
    String name,
    String description,
    Map<String, Object> inputSchema,
    ApprovalRequest.Risk risk) {
  private static final Pattern PROVIDER_SAFE_ID = Pattern.compile("^[a-zA-Z0-9_-]+$");

  public ToolDefinition {
    if (!isValidId(id)) {
      throw new IllegalArgumentException("Tool id must match ^[a-zA-Z0-9_-]+$: " + id);
    }
    inputSchema = inputSchema == null ? Map.of("type", "object") : Map.copyOf(inputSchema);
  }

  public static boolean isValidId(String id) {
    return id != null && PROVIDER_SAFE_ID.matcher(id).matches();
  }
}
