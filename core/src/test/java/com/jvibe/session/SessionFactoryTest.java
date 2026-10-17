package com.jvibe.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.config.JvibePaths;
import com.jvibe.git.GitService;
import com.jvibe.provider.ProviderConfigService;
import com.jvibe.tool.ToolRegistryFactory;
import com.jvibe.tool.builtin.BuiltinToolSet;
import com.jvibe.tool.dynamic.DynamicToolRegistry;
import com.jvibe.tool.dynamic.DynamicToolManagementTools;
import com.jvibe.tool.dynamic.ToolBuildService;
import com.jvibe.workspace.Workspace;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionFactoryTest {
  @TempDir java.nio.file.Path home;

  @Test
  void createsWorkspaceSessionsWithWorkspaceMode() {
    var factory = factory();

    var session = factory.create(new Workspace(home));

    assertEquals(AgentSession.Mode.WORKSPACE, session.mode());
  }

  @Test
  void createsDefaultChatSessionsWithChatMode() {
    var factory = factory();

    var session = factory.createDefaultChat(home.resolve("tmp"), List::of);

    assertEquals(AgentSession.Mode.CHAT, session.mode());
  }

  @Test
  void createsJdbcSessionsWithoutTools() {
    var session = factory().createJdbc(home.resolve("tmp"));

    assertEquals(AgentSession.Mode.JDBC, session.mode());
    assertTrue(session.toolRegistry().definitions().isEmpty());
  }

  private SessionFactory factory() {
    var paths = JvibePaths.under(home);
    var dynamicTools = new DynamicToolRegistry(paths, new ToolBuildService(), new GitService(new com.jvibe.process.ProcessRunner()));
    var toolFactory = new ToolRegistryFactory(
        new BuiltinToolSet(List.of(com.jvibe.tool.builtin.FileReadTool.registered(), com.jvibe.tool.builtin.FileWriteTool.registered())),
        dynamicTools,
        new DynamicToolManagementTools(paths, dynamicTools));
    return new SessionFactory(toolFactory, new ProviderConfigService(paths));
  }
}
