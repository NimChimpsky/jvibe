package com.jvibe.session;

import com.jvibe.provider.ProviderConfigService;
import com.jvibe.ssh.RemoteSshToolRegistryFactory;
import com.jvibe.ssh.SshConnection;
import com.jvibe.tool.ToolRegistryFactory;
import com.jvibe.tool.ToolRegistry;
import com.jvibe.workspace.Workspace;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

@Singleton
public final class SessionFactory {
  private final ToolRegistryFactory toolRegistryFactory;
  private final ProviderConfigService providerConfigService;
  private final RemoteSshToolRegistryFactory remoteSshTools;

  @Inject
  public SessionFactory(
      ToolRegistryFactory toolRegistryFactory,
      ProviderConfigService providerConfigService,
      RemoteSshToolRegistryFactory remoteSshTools) {
    this.toolRegistryFactory = toolRegistryFactory;
    this.providerConfigService = providerConfigService;
    this.remoteSshTools = remoteSshTools;
  }

  SessionFactory(ToolRegistryFactory toolRegistryFactory, ProviderConfigService providerConfigService) {
    this(toolRegistryFactory, providerConfigService, null);
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
    var session = new AgentSession(workspace, toolRegistryFactory.createDefaultChat(references), AgentSession.Mode.CHAT);
    var settings = providerConfigService.settings(providerConfigService.defaultProvider());
    session.selectModel(settings.provider(), settings.selectedModel());
    return session;
  }

  public AgentSession createRemoteSsh(Path tmp, Supplier<List<SshConnection>> attachedSessions) {
    var workspace = new Workspace(tmp);
    if (remoteSshTools == null) {
      throw new IllegalStateException("Remote SSH tools are not configured");
    }
    var registry = remoteSshTools.create(attachedSessions);
    var session = new AgentSession(workspace, registry, AgentSession.Mode.REMOTE_SSH);
    var settings = providerConfigService.settings(providerConfigService.defaultProvider());
    session.selectModel(settings.provider(), settings.selectedModel());
    return session;
  }

  public AgentSession createJdbc(Path tmp) {
    return toolFree(tmp, AgentSession.Mode.JDBC);
  }

  private AgentSession toolFree(Path root, AgentSession.Mode mode) {
    var session = new AgentSession(new Workspace(root), new ToolRegistry(), mode);
    var settings = providerConfigService.settings(providerConfigService.defaultProvider());
    session.selectModel(settings.provider(), settings.selectedModel());
    return session;
  }

}
