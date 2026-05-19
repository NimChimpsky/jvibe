package com.jvibe.provider;

import com.jvibe.session.Message;
import com.jvibe.tool.ToolDefinition;
import java.util.List;
import java.util.Map;

public record ProviderRequest(
    String provider,
    String model,
    String systemPrompt,
    List<Message> messages,
    List<ToolDefinition> tools,
    Map<String, Object> options) {}
