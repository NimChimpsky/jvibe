package com.jvibe.session;

import com.jvibe.provider.ProviderConfigService;
import com.jvibe.tool.ToolRegistryFactory;
import com.jvibe.workspace.Workspace;
import jakarta.inject.Singleton;
import java.nio.file.Path;

@Singleton
public final class SessionFactory {
  private final ToolRegistryFactory toolRegistryFactory;
  private final ProviderConfigService providerConfigService;

  public SessionFactory(ToolRegistryFactory toolRegistryFactory, ProviderConfigService providerConfigService) {
    this.toolRegistryFactory = toolRegistryFactory;
    this.providerConfigService = providerConfigService;
  }

  public AgentSession create(Path workspaceRoot) {
    return create(new Workspace(workspaceRoot));
  }

  public AgentSession create(Workspace workspace) {
    var session = new AgentSession(workspace, toolRegistryFactory.create(workspace));
    var settings = providerConfigService.settings(providerConfigService.defaultProvider());
    session.selectModel(settings.provider(), settings.selectedModel());
    return session;
  }
}
