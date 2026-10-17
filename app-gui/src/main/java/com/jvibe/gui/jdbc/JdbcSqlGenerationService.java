package com.jvibe.gui.jdbc;

import com.jvibe.agent.AgentRunner;
import com.jvibe.events.AgentEvent;
import com.jvibe.session.AgentSession;
import io.avaje.inject.External;
import jakarta.inject.Singleton;

@Singleton
public final class JdbcSqlGenerationService {
  private final AgentRunner runner;

  public JdbcSqlGenerationService(@External AgentRunner runner) {
    this.runner = runner;
  }

  public String generate(AgentSession session, String request, String schema) {
    if (request == null || request.isBlank()) {
      throw new IllegalArgumentException("Describe the required query or change first");
    }
    var prompt = """
        Database schema:
        %s

        User request:
        %s
        """.formatted(schema == null ? "[schema unavailable]" : schema, request.trim());
    var sql = new StringBuilder();
    var error = new StringBuilder();
    try (var events = runner.run(session, prompt)) {
      events.forEach(event -> {
        if (event.type() == AgentEvent.Type.ASSISTANT_DELTA) {
          sql.append(event.message());
        } else if (event.type() == AgentEvent.Type.ERROR) {
          error.append(event.message());
        }
      });
    }
    if (!error.isEmpty()) {
      throw new IllegalStateException(error.toString());
    }
    var result = removeMarkdownFence(sql.toString());
    if (result.isBlank()) {
      throw new IllegalStateException("The selected model did not generate SQL");
    }
    return result;
  }

  static String removeMarkdownFence(String value) {
    var text = value == null ? "" : value.trim();
    if (!text.startsWith("```")) {
      return text;
    }
    var firstLineEnd = text.indexOf('\n');
    if (firstLineEnd < 0) {
      return "";
    }
    text = text.substring(firstLineEnd + 1);
    if (text.endsWith("```")) {
      text = text.substring(0, text.length() - 3);
    }
    return text.trim();
  }
}
