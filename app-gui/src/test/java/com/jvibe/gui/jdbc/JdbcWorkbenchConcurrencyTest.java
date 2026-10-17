package com.jvibe.gui.jdbc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jvibe.config.ConfigService;
import com.jvibe.config.JvibePaths;
import com.jvibe.gui.controller.ModelSelectionController;
import com.jvibe.jdbc.JdbcConnectionConfig;
import com.jvibe.jdbc.JdbcCredentialStore;
import com.jvibe.jdbc.JdbcDatabaseService;
import com.jvibe.session.SessionFactory;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcWorkbenchConcurrencyTest {
  @TempDir Path home;

  @Test
  void switchingConnectionDiscardsPendingSqlAndCapturesRequestBeforeBackgroundWork() throws Exception {
    var database = mock(JdbcDatabaseService.class);
    var credentials = new JdbcCredentialStore();
    var generation = mock(JdbcSqlGenerationService.class);
    var first = JdbcConnectionConfig.create("first", "jdbc:test:first", "", "", "");
    var second = JdbcConnectionConfig.create("second", "jdbc:test:second", "", "", "");
    credentials.put(first.id(), new char[0]);
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var finished = new CountDownLatch(1);
    when(database.schemaSummary(eq(first), any())).thenAnswer(call -> {
      started.countDown();
      assertTrue(release.await(5, TimeUnit.SECONDS));
      return "schema";
    });
    when(generation.generate(any(), eq("original request"), eq("schema"))).thenReturn("SELECT first_database");
    var reference = new AtomicReference<JdbcWorkbenchPanel>();
    SwingUtilities.invokeAndWait(() -> {
      var panel = new JdbcWorkbenchPanel(database, credentials, mock(SessionFactory.class), generation,
          mock(ModelSelectionController.class), new ConfigService(JvibePaths.under(home)), message -> {});
      reference.set(panel);
      panel.setConnection(first);
      var editors = components(panel, JTextArea.class);
      editors.get(0).setText("original request");
      var button = components(panel, JButton.class).stream().filter(value -> "Generate SQL".equals(value.getText())).findFirst().orElseThrow();
      button.addPropertyChangeListener("enabled", event -> { if (Boolean.TRUE.equals(event.getNewValue())) finished.countDown(); });
      button.doClick();
    });
    try {
      assertTrue(started.await(5, TimeUnit.SECONDS));
      SwingUtilities.invokeAndWait(() -> {
        components(reference.get(), JTextArea.class).get(0).setText("changed request");
        reference.get().setConnection(second);
      });
    } finally {
      release.countDown();
    }
    assertTrue(finished.await(5, TimeUnit.SECONDS));
    SwingUtilities.invokeAndWait(() -> assertEquals("", components(reference.get(), JTextArea.class).get(1).getText()));
    verify(generation).generate(any(), eq("original request"), eq("schema"));
    credentials.close();
  }

  private static <T> List<T> components(Component root, Class<T> type) {
    var found = new ArrayList<T>();
    if (type.isInstance(root)) found.add(type.cast(root));
    if (root instanceof Container container) {
      for (var child : container.getComponents()) found.addAll(components(child, type));
    }
    return found;
  }
}
