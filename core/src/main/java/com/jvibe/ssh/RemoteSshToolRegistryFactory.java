package com.jvibe.ssh;

import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolRegistry;
import com.jvibe.tool.ToolResult;
import jakarta.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Singleton
public final class RemoteSshToolRegistryFactory {
  private final SshConnectionService connections;
  private final SshClientService client;
  private final SshReadOnlyPolicy readOnlyPolicy;

  public RemoteSshToolRegistryFactory(
      SshConnectionService connections,
      SshClientService client,
      SshReadOnlyPolicy readOnlyPolicy) {
    this.connections = connections;
    this.client = client;
    this.readOnlyPolicy = readOnlyPolicy;
  }

  public ToolRegistry create(Supplier<List<SshConnection>> attachedSessions) {
    var registry = new ToolRegistry();
    registry.register(new RegisteredTool(definition("ssh_session_list", "List attached SSH sessions", "List SSH sessions attached to this chat.", object()), (call, context) -> {
      var sessions = attachedSessions.get().stream()
          .map(session -> Map.of(
              "sessionId", session.id(),
              "name", session.name(),
              "host", session.host(),
              "user", session.user(),
              "accessMode", session.accessMode().name()))
          .toList();
      return ToolResult.success(call.id(), Map.of("sessions", sessions), "Listed " + sessions.size() + " SSH sessions");
    }));
    registry.register(new RegisteredTool(
        definition("ssh_exec", "Run SSH command", "Run a command on an attached SSH session.", object(Map.of(
            "sessionId", string("Attached SSH session id."),
            "command", string("Remote command to run.")), "sessionId", "command")),
        (call, context) -> execute(call, attachedSessions.get(), string(call, "command"))));
    registry.register(new RegisteredTool(
        definition("ssh_file_read", "Read remote file", "Read a file from an attached SSH session.", object(Map.of(
            "sessionId", string("Attached SSH session id."),
            "path", string("Remote file path.")), "sessionId", "path")),
        (call, context) -> execute(call, attachedSessions.get(), "cat " + shellQuote(string(call, "path")))));
    registry.register(new RegisteredTool(
        definition("ssh_file_list", "List remote files", "List files from a remote directory.", object(Map.of(
            "sessionId", string("Attached SSH session id."),
            "path", string("Remote directory path.")), "sessionId", "path")),
        (call, context) -> execute(call, attachedSessions.get(), "find " + shellQuote(string(call, "path")) + " -maxdepth 1 -print")));
    registry.register(new RegisteredTool(
        definition("ssh_disk_usage", "Remote disk usage", "Show remote disk usage.", object(Map.of(
            "sessionId", string("Attached SSH session id."),
            "path", string("Remote path.")), "sessionId", "path")),
        (call, context) -> execute(call, attachedSessions.get(), "du -sh " + shellQuote(string(call, "path")))));
    return registry;
  }

  private ToolResult execute(ToolCall call, List<SshConnection> attached, String command) throws Exception {
    var connection = attached.stream()
        .filter(session -> session.id().equals(string(call, "sessionId")))
        .findFirst()
        .or(() -> connections.find(string(call, "sessionId")).filter(attached::contains))
        .orElse(null);
    if (connection == null) {
      return ToolResult.failure(call.id(), "SSH session is not attached to this chat");
    }
    if (connection.accessMode() == SshAccessMode.READ_ONLY && !readOnlyPolicy.isAllowed(command)) {
      return ToolResult.failure(call.id(), "Command blocked by read-only SSH policy");
    }
    var result = client.execute(connection, command);
    var payload = Map.<String, Object>of(
        "exitCode", result.exitCode(), "stdout", result.stdout(), "stderr", result.stderr());
    var summary = connection.name() + " exited " + result.exitCode();
    return result.exitCode() == 0
        ? ToolResult.success(call.id(), payload, summary)
        : ToolResult.failure(call.id(), payload, summary);
  }

  private static ToolDefinition definition(String id, String name, String description, Map<String, Object> schema) {
    return new ToolDefinition(id, name, description, schema);
  }

  private static Map<String, Object> object() {
    return Map.of("type", "object", "properties", Map.of(), "additionalProperties", false);
  }

  private static Map<String, Object> object(Map<String, Object> properties, String... required) {
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

  private static String string(ToolCall call, String key) {
    var value = call.arguments().get(key);
    if (value == null) {
      throw new IllegalArgumentException("Missing argument: " + key);
    }
    return value.toString();
  }

  private static String shellQuote(String value) {
    return "'" + value.replace("'", "'\"'\"'") + "'";
  }
}
