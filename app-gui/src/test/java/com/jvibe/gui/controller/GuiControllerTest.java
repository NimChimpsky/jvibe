package com.jvibe.gui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jvibe.agent.AgentRunner;
import com.jvibe.config.JvibePaths;
import com.jvibe.events.AgentEvent;
import com.jvibe.provider.ModelDescriptor;
import com.jvibe.provider.ProviderClient;
import com.jvibe.provider.ProviderConfigService;
import com.jvibe.provider.ProviderEvent;
import com.jvibe.provider.ProviderRegistry;
import com.jvibe.provider.ProviderRequest;
import com.jvibe.provider.ProviderSettings;
import com.jvibe.tool.ToolRegistry;
import com.jvibe.workspace.Workspace;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuiControllerTest {
  @TempDir
  Path home;

  @Test
  void settingsControllerSavesAndRefreshesModels() {
    var config = new ProviderConfigService(JvibePaths.under(home));
    var provider = new StubProviderClient("openai", List.of(model("openai", "gpt-refreshed")));
    var controller = new ProviderSettingsController(config, ProviderRegistry.of(provider));

    controller.save(new ProviderSettings("openai", "https://api.openai.com/v1", "key", "gpt-saved", "none", true));

    assertEquals("gpt-saved", controller.settings("openai").selectedModel());
    assertEquals(List.of("jvibe", "openai", "anthropic", "mistral", "deepseek", "qwen"),
        controller.configurableProviders().stream().map(definition -> definition.id()).toList());
    assertEquals("Credits: unavailable", controller.credits(controller.settings("jvibe")));
    assertEquals(List.of("gpt-refreshed"),
        controller.refreshModels(controller.settings("openai")).stream().map(ModelDescriptor::id).toList());
  }

  @Test
  void settingsControllerSavesAllProviderTabs() {
    var config = new ProviderConfigService(JvibePaths.under(home));
    var provider = new StubProviderClient("openai", List.of(model("openai", "gpt-refreshed")));
    var controller = new ProviderSettingsController(config, ProviderRegistry.of(provider));

    controller.saveAll(List.of(
        new ProviderSettings("openai", "https://api.openai.com/v1", "openai-key", "gpt-saved", "none", true),
        new ProviderSettings("anthropic", "https://api.anthropic.com/v1", "anthropic-key", "claude-saved", "off", true)));

    assertEquals("gpt-saved", controller.settings("openai").selectedModel());
    assertEquals("claude-saved", controller.settings("anthropic").selectedModel());
  }

  @Test
  void modelSelectionListsEnabledProviderModelsAndAppliesSelection() throws Exception {
    var config = new ProviderConfigService(JvibePaths.under(home));
    config.save(new ProviderSettings("openai", "https://api.openai.com/v1", "key", "gpt-saved", "none", true));
    var provider = new StubProviderClient("openai", List.of(model("openai", "gpt-extra")));
    var controller = new ModelSelectionController(config, ProviderRegistry.of(provider));

    assertEquals(List.of("openai/gpt-saved", "openai/gpt-extra"),
        controller.choices().stream().map(ModelChoice::display).toList());

    var session = new com.jvibe.session.AgentSession(new Workspace(home), new ToolRegistry());
    controller.apply(session, "openai/gpt-extra");

    assertEquals("openai", session.provider());
    assertEquals("gpt-extra", session.model());
  }

  @Test
  void modelSelectionDoesNotExposeLocalEcho() {
    var config = new ProviderConfigService(JvibePaths.under(home));
    var provider = new StubProviderClient("local", List.of(model("local", "echo")));
    var controller = new ModelSelectionController(config, ProviderRegistry.of(provider));

    assertTrue(controller.choices().isEmpty());
  }

  @Test
  void promptControllerTrimsPromptAndStreamsRunnerEvents() throws Exception {
    var runner = mock(AgentRunner.class);
    var session = new com.jvibe.session.AgentSession(new Workspace(home), new ToolRegistry());
    var expected = new AgentEvent(AgentEvent.Type.ASSISTANT_DELTA, "hi");
    when(runner.run(session, "hello")).thenReturn(Stream.of(expected));

    var controller = new PromptController(runner);

    assertEquals(List.of(expected), controller.submit(session, "  hello  ").toList());
    assertTrue(controller.submit(session, "   ").toList().isEmpty());
    verify(runner).run(session, "hello");
  }

  private static ModelDescriptor model(String provider, String id) {
    return new ModelDescriptor(provider, id, id, 128000, true);
  }

  private record StubProviderClient(String providerId, List<ModelDescriptor> models) implements ProviderClient {
    @Override
    public Stream<ProviderEvent> stream(ProviderRequest request) {
      return Stream.empty();
    }

    @Override
    public List<ModelDescriptor> refreshModels(ProviderSettings settings) {
      return models;
    }
  }
}
