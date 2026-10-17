package com.jvibe.ssh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.tool.ToolResult;
import com.jvibe.workspace.Workspace;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SshToolSetTest {
  @TempDir Path tmp;
  private final SshConnectionService connections = mock(SshConnectionService.class);
  private final SshClientService client = mock(SshClientService.class);
  private final SshConnection attached = new SshConnection("prod", "Production", "host", "user", 22, "", "");

  @Test
  void runsOnTheCurrentConnectionSettings() throws Exception {
    var moved = new SshConnection("prod", "Production", "new-host", "user", 22, "", "");
    when(connections.find("prod")).thenReturn(Optional.of(moved));
    when(client.execute(eq(moved), eq("uptime"), any(), any())).thenReturn(new SshCommandResult(0, "up", ""));

    var result = execute(Map.of("serverId", "prod", "command", "uptime"));

    assertTrue(result.success());
    verify(client).execute(eq(moved), eq("uptime"), any(), any());
  }

  @Test
  void refusesServersThatAreNotAttachedOrWereDeleted() {
    assertFalse(execute(Map.of("serverId", "other", "command", "pwd")).success());
    when(connections.find("prod")).thenReturn(Optional.empty());
    assertFalse(execute(Map.of("serverId", "prod", "command", "pwd")).success());
    verifyNoInteractions(client);
  }

  @Test
  void contextListsAttachedServerIds() {
    assertTrue(SshToolSet.context(List.of(attached)).contains("serverId=prod name=Production address=user@host"));
    assertTrue(SshToolSet.context(List.of()).contains("No servers are attached"));
  }

  private ToolResult execute(Map<String, Object> arguments) {
    var registry = new SshToolSet(connections, client).registry(() -> List.of(attached));
    return registry.execute(new ToolCall("call", "ssh_exec", arguments), ToolExecutionContext.defaults(new Workspace(tmp)));
  }
}
