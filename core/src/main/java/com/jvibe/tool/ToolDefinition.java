package com.jvibe.tool;

import com.jvibe.approval.ApprovalRequest;
import java.util.Map;

public record ToolDefinition(
    String id,
    String name,
    String description,
    Map<String, Object> inputSchema,
    ApprovalRequest.Risk risk) {}
