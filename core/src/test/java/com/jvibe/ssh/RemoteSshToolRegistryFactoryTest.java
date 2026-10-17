package com.jvibe.ssh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.jvibe.tool.ToolCall;
import com.jvibe.tool.ToolExecutionContext;
import com.jvibe.workspace.Workspace;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemoteSshToolRegistryFactoryTest {
  @TempDir Path tmp;
  private final SshConnectionService connections = mock(SshConnectionService.class);
  private final SshClientService client = mock(SshClientService.class);
  private final SshConnection attached = new SshConnection(
      "prod", "Production", "host", "user", 22, "", SshAccessMode.FULL_ACCESS, "");

  @Test
  void permissionDowngradeAppliesToPreviouslyAttachedSession() {
    when(connections.find("prod")).thenReturn(Optional.of(attached.withAccessMode(SshAccessMode.READ_ONLY)));
    var result = execute("ssh_exec", Map.of("sessionId", "prod", "command", "rm /tmp/file"));
    assertFalse(result.success());
    verifyNoInteractions(client);
  }

  @Test
  void deletedConnectionCannotExecute() {
    when(connections.find("prod")).thenReturn(Optional.empty());
    assertFalse(execute("ssh_exec", Map.of("sessionId", "prod", "command", "pwd")).success());
    verifyNoInteractions(client);
  }

  @Test
  void fullAccessUsesCurrentConnectionAndAllowsCommands() throws Exception {
    when(connections.find("prod")).thenReturn(Optional.of(attached));
    when(client.execute(eq(attached), eq("touch /tmp/file"), any(), any())).thenReturn(new SshCommandResult(0, "", ""));
    assertTrue(execute("ssh_exec", Map.of("sessionId", "prod", "command", "touch /tmp/file")).success());
    verify(client).execute(eq(attached), eq("touch /tmp/file"), any(), any());
  }

  @Test
  void filePathCannotBeInterpretedAsCommandOptions() throws Exception {
    when(connections.find("prod")).thenReturn(Optional.of(attached));
    when(client.execute(eq(attached), anyString(), any(), any())).thenReturn(new SshCommandResult(0, "", ""));
    assertTrue(execute("ssh_file_list", Map.of("sessionId", "prod", "path", "-delete")).success());
    verify(client).execute(eq(attached), eq("find './-delete' -maxdepth 1 -print"), any(), any());
    assertTrue(execute("ssh_file_read", Map.of("sessionId", "prod", "path", "-v")).success());
    verify(client).execute(eq(attached), eq("cat -- '-v'"), any(), any());
  }

  private com.jvibe.tool.ToolResult execute(String tool, Map<String, Object> arguments) {
    var registry = new RemoteSshToolRegistryFactory(connections, client, new SshReadOnlyPolicy())
        .create(() -> List.of(attached));
    return registry.execute(new ToolCall("call", tool, arguments), ToolExecutionContext.defaults(new Workspace(tmp)));
  }
}
