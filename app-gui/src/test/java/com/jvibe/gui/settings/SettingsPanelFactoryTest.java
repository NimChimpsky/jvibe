package com.jvibe.gui.settings;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.jvibe.config.*;
import com.jvibe.gui.controller.ProviderSettingsController;
import com.jvibe.provider.*;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsPanelFactoryTest {
  @TempDir Path home;

  @Test void exposesOnlyJvibeAccountCustomisationsAndThemeAndPreservesStoredKey() throws Exception {
    var paths = JvibePaths.under(home);
    var config = new ConfigService(paths);
    var providers = new ProviderConfigService(paths);
    providers.save(new ProviderSettings("jvibe", "", "stored-secret", "jvibe-coder", "none", true));
    var account = new ProviderSettingsController(providers, mock(JvibeProviderClient.class));
    var saved = new AtomicInteger();
    SwingUtilities.invokeAndWait(() -> {
      var theme = new JComboBox<>(new String[] {"Dracula"});
      var panel = new SettingsPanelFactory(config, account).create(theme, saved::incrementAndGet);
      assertEquals(0, components(panel, JPasswordField.class).getFirst().getPassword().length);
      assertTrue(components(panel, JCheckBox.class).isEmpty());
      assertEquals(3, components(panel, JComboBox.class).size());
      assertFalse(components(panel, JLabel.class).stream().anyMatch(label -> "Providers".equals(label.getText())));
      components(panel, JTextArea.class).getFirst().setText("Prefer small focused changes.");
      components(panel, JSpinner.class).getFirst().setValue(25);
      components(panel, JButton.class).stream().filter(button -> "Save".equals(button.getText())).findFirst().orElseThrow().doClick();
    });
    assertEquals(1, saved.get());
    assertEquals("stored-secret", providers.settings("jvibe").apiKey());
    assertEquals(25, config.config().maxToolIterations());
    assertEquals("Prefer small focused changes.", config.config().customInstructions());
  }

  static <T> List<T> components(Component component, Class<T> type) {
    var found = new ArrayList<T>();
    if (type.isInstance(component)) found.add(type.cast(component));
    if (component instanceof Container container) for (var child : container.getComponents()) found.addAll(components(child, type));
    return found;
  }
}
