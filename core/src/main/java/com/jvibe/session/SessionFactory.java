package com.jvibe.session;

import com.jvibe.provider.ProviderConfigService;
import com.jvibe.tool.ToolRegistryFactory;
import com.jvibe.workspace.Workspace;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

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
    var session = new AgentSession(workspace, toolRegistryFactory.create(workspace), AgentSession.Mode.WORKSPACE);
    var settings = providerConfigService.settings(providerConfigService.defaultProvider());
    session.selectModel(settings.provider(), settings.selectedModel());
    return session;
  }

  public AgentSession createDefaultChat(Path tmp, Supplier<List<Path>> references) {
    var workspace = new Workspace(tmp);
    var session = new AgentSession(workspace, toolRegistryFactory.createDefaultChat(tmp, references), AgentSession.Mode.CHAT);
    var settings = providerConfigService.settings(providerConfigService.defaultProvider());
    session.selectModel(settings.provider(), settings.selectedModel());
    return session;
  }
}
