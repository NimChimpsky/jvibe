package com.jvibe.gui.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.jvibe.agent.AgentRunner;
import com.jvibe.config.JvibePaths;
import com.jvibe.events.AgentEvent;
import com.jvibe.provider.*;
import com.jvibe.session.AgentSession;
import com.jvibe.tool.ToolRegistry;
import com.jvibe.workspace.Workspace;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuiControllerTest {
  @TempDir Path home;

  @Test void accountControllerSavesOnlyJvibeAndDelegatesRefresh() {
    var config = new ProviderConfigService(JvibePaths.under(home));
    var client = mock(JvibeProviderClient.class);
    var controller = new ProviderSettingsController(config, client);
    controller.save(new ProviderSettings("jvibe", "", "key", "custom", "high", true));
    when(client.refreshModels(any())).thenReturn(List.of(new ModelDescriptor("jvibe", "fresh", "Fresh", 1000, true)));
    when(client.creditsLabel(any())).thenReturn("Credits: 3");
    assertEquals("custom", controller.settings().selectedModel());
    assertEquals("fresh", controller.refreshModels(controller.settings()).getFirst().id());
    assertEquals("Credits: 3", controller.credits(controller.settings()));
  }

  @Test void modelSelectionCannotSwitchBackendsByTypingProviderPrefix() {
    var config = new ProviderConfigService(JvibePaths.under(home));
    var client = mock(JvibeProviderClient.class);
    when(client.models()).thenReturn(List.of(new ModelDescriptor("jvibe", "extra", "Extra", 1000, true)));
    var controller = new ModelSelectionController(config, client);
    assertEquals(List.of("jvibe-coder", "extra"), controller.choices().stream().map(ModelChoice::display).toList());
    var session = new AgentSession(new Workspace(home), new ToolRegistry());
    controller.apply(session, "extra");
    assertEquals("jvibe", session.provider());
    assertEquals("extra", session.model());
    controller.apply(session, "other/model");
    assertEquals("jvibe", session.provider());
    assertEquals("extra", session.model());
  }

  @Test void promptControllerTrimsAndStreams() {
    var runner = mock(AgentRunner.class);
    var session = new AgentSession(new Workspace(home), new ToolRegistry());
    var expected = new AgentEvent(AgentEvent.Type.ASSISTANT_DELTA, "hi");
    when(runner.run(eq(session), eq("hello"), any())).thenReturn(Stream.of(expected));
    var controller = new PromptController(runner);
    assertEquals(List.of(expected), controller.submit(session, "  hello  ").toList());
    assertTrue(controller.submit(session, "   ").toList().isEmpty());
    verify(runner).run(eq(session), eq("hello"), any());
  }
}
