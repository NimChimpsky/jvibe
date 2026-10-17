package com.jvibe.agent;

import com.jvibe.tool.ToolResult;
import io.avaje.jsonb.Json;
import io.avaje.jsonb.Jsonb;
import java.util.Map;

/** Tool result as sent back to the model for the next turn. */
@Json
record ModelToolResult(boolean success, String summary, Map<String, Object> payload) {
  static String encode(ToolResult result) {
    return Jsonb.instance().type(ModelToolResult.class)
        .toJson(new ModelToolResult(result.success(), result.summary(), result.payload()));
  }
}
