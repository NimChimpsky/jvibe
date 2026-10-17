package com.jvibe.ssh;

import com.jvibe.tool.RegisteredTool;
import com.jvibe.tool.ToolArguments;
import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolRegistry;
import com.jvibe.tool.ToolResult;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.function.Supplier;

/** The Servers tab tool: {@code ssh_exec} on a server attached to the conversation. */
@Singleton
public final class SshToolSet {
  private final SshConnectionService connections;
  private final SshClientService client;

  public SshToolSet(SshConnectionService connections, SshClientService client) {
    this.connections = connections;
    this.client = client;
  }

  public ToolRegistry registry(Supplier<List<SshConnection>> attached) {
    return new ToolRegistry(List.of(
        RegisteredTool.changing("ssh_exec", (call, context) -> execute(call, attached.get(), context))));
  }

  /** Session context listing the attached servers by id. */
  public static String context(List<SshConnection> attached) {
    if (attached.isEmpty()) {
      return "No servers are attached. Ask the user to attach servers with @name.";
    }
    var text = new StringBuilder("Attached servers:\n");
    attached.forEach(server -> text.append("- serverId=").append(server.id())
        .append(" name=").append(server.name())
        .append(" address=").append(server.address()).append('\n'));
    return text.toString();
  }

  private ToolResult execute(ToolCall call, List<SshConnection> attached, ToolExecutionContext context)
      throws Exception {
    var id = ToolArguments.string(call.arguments(), "serverId");
    var command = ToolArguments.string(call.arguments(), "command");
    // Attachments authorise ids; the stored connection supplies current host and key settings.
    var connection = attached.stream().anyMatch(server -> server.id().equals(id))
        ? connections.find(id).orElse(null) : null;
    if (connection == null) {
      return ToolResult.failure(call.id(), "Server " + id + " is not attached to this conversation");
    }
    var result = client.execute(connection, command, context.timeout(), context.cancelled());
    return ToolResult.exit(call.id(), result.exitCode(), result.stdout(), result.stderr(),
        connection.name() + ": " + command + " (exit " + result.exitCode() + ")");
  }
}
