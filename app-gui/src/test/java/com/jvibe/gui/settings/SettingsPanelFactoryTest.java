package com.jvibe.gui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvibe.api.Account;
import com.jvibe.api.AccountService;
import com.jvibe.api.JvibeClient;
import com.jvibe.api.Thinking;
import com.jvibe.config.ConfigService;
import com.jvibe.config.JvibePaths;
import com.jvibe.session.Mode;
import io.avaje.inject.BeanScope;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsPanelFactoryTest {
  @TempDir Path home;

  @Test
  void savesKeyThinkingAndPerTabCustomisation() throws Exception {
    try (var scope = BeanScope.builder().bean(JvibePaths.class, JvibePaths.under(home)).build()) {
      var accounts = scope.get(AccountService.class);
      var config = scope.get(ConfigService.class);
      accounts.save(new Account("me@example.com", "jvibe_stored", ""));
      SwingUtilities.invokeAndWait(() -> {
        var panel = new SettingsPanelFactory(config, accounts, scope.get(JvibeClient.class))
            .create(new JComboBox<>(new String[] {"Dracula"}));
        var key = components(panel, JPasswordField.class).getFirst();
        assertEquals("jvibe_stored", new String(key.getPassword()));
        assertEquals(3, components(panel, JCheckBox.class).size(), "Build, Servers and Databases");
        assertEquals(Mode.values().length, components(panel, JTextArea.class).size());

        key.setText("jvibe_pasted");
        components(panel, JComboBox.class).stream().filter(box -> box.getItemCount() == Thinking.values().length)
            .findFirst().orElseThrow().setSelectedItem(Thinking.LOW);
        components(panel, JSpinner.class).getFirst().setValue(40);
        components(panel, JTextArea.class).get(Mode.SERVERS.ordinal()).setText("Hosts run RHEL 8.");
        components(panel, JCheckBox.class).get(1).setSelected(true);
        components(panel, JButton.class).stream().filter(button -> "Save".equals(button.getText()))
            .findFirst().orElseThrow().doClick();
      });
      assertEquals("jvibe_pasted", accounts.apiKey());
      var saved = config.config();
      assertEquals(Thinking.LOW, saved.thinkingLevel());
      assertEquals(40, saved.maxToolIterations());
      assertEquals("Hosts run RHEL 8.", saved.mode(Mode.SERVERS).instructions());
      assertTrue(saved.mode(Mode.SERVERS).askBeforeRunning());
      assertFalse(saved.mode(Mode.BUILD).askBeforeRunning());
    }
  }

  static <T> List<T> components(Component component, Class<T> type) {
    var found = new ArrayList<T>();
    if (type.isInstance(component)) found.add(type.cast(component));
    if (component instanceof Container container) {
      for (var child : container.getComponents()) found.addAll(components(child, type));
    }
    return found;
  }
}
