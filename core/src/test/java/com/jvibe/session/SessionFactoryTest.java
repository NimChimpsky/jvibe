package com.jvibe.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

  private SessionFactory factory() {
    var paths = JvibePaths.under(home);
    var dynamicTools = new DynamicToolRegistry(paths, new ToolBuildService(), new GitService());
    var toolFactory = new ToolRegistryFactory(
        new BuiltinToolSet(List.of()),
        dynamicTools,
        new DynamicToolManagementTools(paths, dynamicTools));
    return new SessionFactory(toolFactory, new ProviderConfigService(paths));
  }
}
