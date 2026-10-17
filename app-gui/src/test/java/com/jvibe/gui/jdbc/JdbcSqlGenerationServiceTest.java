package com.jvibe.gui.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jvibe.agent.AgentRunner;
import com.jvibe.events.AgentEvent;
import com.jvibe.config.ConfigService;
import com.jvibe.gui.controller.ModelSelectionController;
import com.jvibe.jdbc.JdbcConnectionRepository;
import com.jvibe.jdbc.JdbcCredentialStore;
import com.jvibe.jdbc.JdbcDatabaseService;
import com.jvibe.gui.jdbc.JdbcPanelFactory;
import com.jvibe.session.AgentSession;
import com.jvibe.session.SessionFactory;
import io.avaje.inject.BeanScope;
import java.awt.Component;
import java.util.stream.Stream;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JSplitPane;
import org.junit.jupiter.api.Test;

class JdbcSqlGenerationServiceTest {
  @Test
  void generatesSqlAndRemovesMarkdownFence() {
    var runner = mock(AgentRunner.class);
    var session = mock(AgentSession.class);
    when(runner.run(any(), contains("User request:"))).thenReturn(Stream.of(
        new AgentEvent(AgentEvent.Type.ASSISTANT_DELTA, "```sql\nSELECT * FROM projects;\n```"),
        new AgentEvent(AgentEvent.Type.DONE, "Done")));

    var sql = new JdbcSqlGenerationService(runner).generate(session, "list projects", "projects (id INTEGER)");

    assertEquals("SELECT * FROM projects;", sql);
  }

  @Test
  void jdbcPanelFactoryIsInjectable() {
    try (var scope = BeanScope.builder().build()) {
      assertNotNull(scope.get(JdbcPanelFactory.class));
    }
  }

  @Test
  void panelUsesThreeColumnsAndProvidesGenerationAndExecutionActions() {
    var repository = mock(JdbcConnectionRepository.class);
    when(repository.connections()).thenReturn(java.util.List.of());
    var modelSelection = mock(ModelSelectionController.class);
    when(modelSelection.choices()).thenReturn(java.util.List.of());
    var factory = new JdbcPanelFactory(
        repository,
        new JdbcCredentialStore(),
        mock(JdbcDatabaseService.class),
        mock(SessionFactory.class),
        mock(JdbcSqlGenerationService.class),
        modelSelection,
        mock(ConfigService.class));

    var panel = factory.create(message -> {});

    assertTrue(panel instanceof JSplitPane first && first.getOrientation() == JSplitPane.HORIZONTAL_SPLIT);
    var first = (JSplitPane) panel;
    assertTrue(first.getRightComponent() instanceof JSplitPane second
        && second.getOrientation() == JSplitPane.HORIZONTAL_SPLIT);
    assertNotNull(findButton(panel, "Generate SQL"));
    assertNotNull(findButton(panel, "Execute SQL"));
    assertNotNull(findButton(panel, "Refresh Stats"));
  }

  private static JButton findButton(Component component, String text) {
    if (component instanceof JButton button && text.equals(button.getText())) {
      return button;
    }
    if (component instanceof JComponent container) {
      for (var child : container.getComponents()) {
        var found = findButton(child, text);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }
}
