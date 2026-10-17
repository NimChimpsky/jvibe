package com.jvibe.jdbc;

import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolArguments;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolRegistry;
import com.jvibe.tool.ToolResult;
import jakarta.inject.Singleton;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Databases tab tools on connections attached to the conversation. Query results go to
 * the UI in full and to the model as a bounded sample.
 */
@Singleton
public final class JdbcToolSet {
  static final int MODEL_ROWS = 50;

  private final JdbcDatabaseService database;
  private final JdbcCredentialStore credentials;

  public JdbcToolSet(JdbcDatabaseService database, JdbcCredentialStore credentials) {
    this.database = database;
    this.credentials = credentials;
  }

  public ToolRegistry registry(Supplier<List<JdbcConnectionConfig>> attached) {
    return new ToolRegistry(List.of(
        RegisteredTool.reading("sql_schema", (call, context) -> schema(call, attached.get(), context)),
        RegisteredTool.changing("sql_execute", (call, context) -> execute(call, attached.get(), context))));
  }

  /** Session context listing the attached connections by id. */
  public static String context(List<JdbcConnectionConfig> attached) {
    if (attached.isEmpty()) {
      return "No database connections are attached. Ask the user to attach one with @name.";
    }
    var text = new StringBuilder("Attached JDBC connections:\n");
    attached.forEach(connection -> text.append("- connectionId=").append(connection.id())
        .append(" name=").append(connection.name())
        .append(" url=").append(connection.jdbcUrl())
        .append(connection.username().isBlank() ? "" : " user=" + connection.username()).append('\n'));
    return text.toString();
  }

  private ToolResult schema(ToolCall call, List<JdbcConnectionConfig> attached, ToolExecutionContext context)
      throws Exception {
    var connection = attached(call, attached);
    if (connection == null) return notAttached(call);
    context.ensureActive();
    var password = credentials.password(connection.id());
    try {
      var summary = database.schemaSummary(connection, password,
          ToolArguments.string(call.arguments(), "tablePattern", ""));
      return ToolResult.success(call.id(), java.util.Map.of("schema", summary),
          connection.name() + ": schema " + ToolArguments.string(call.arguments(), "tablePattern", "%"));
    } finally {
      Arrays.fill(password, '\0');
    }
  }

  private ToolResult execute(ToolCall call, List<JdbcConnectionConfig> attached, ToolExecutionContext context)
      throws Exception {
    var connection = attached(call, attached);
    if (connection == null) return notAttached(call);
    var sql = ToolArguments.string(call.arguments(), "sql");
    context.ensureActive();
    var password = credentials.password(connection.id());
    try {
      var result = database.execute(connection, password, sql);
      return ToolResult.success(call.id(), modelPayload(result), connection.name() + ": " + result.message())
          .withDisplay(result);
    } finally {
      Arrays.fill(password, '\0');
    }
  }

  static java.util.Map<String, Object> modelPayload(JdbcQueryResult result) {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("message", result.message());
    if (result.updateCount() >= 0) payload.put("updateCount", result.updateCount());
    if (!result.columns().isEmpty()) {
      payload.put("columns", result.columns());
      payload.put("rows", result.rows().stream().limit(MODEL_ROWS)
          .map(row -> row.stream().map(String::valueOf).toList()).toList());
      payload.put("rowCount", result.rows().size());
      payload.put("truncated", result.truncated() || result.rows().size() > MODEL_ROWS);
    }
    return payload;
  }

  private static JdbcConnectionConfig attached(ToolCall call, List<JdbcConnectionConfig> attached) {
    var id = ToolArguments.string(call.arguments(), "connectionId");
    return attached.stream().filter(connection -> connection.id().equals(id)).findFirst().orElse(null);
  }

  private static ToolResult notAttached(ToolCall call) {
    return ToolResult.failure(call.id(), "Connection " + call.arguments().get("connectionId")
        + " is not attached to this conversation");
  }
}
