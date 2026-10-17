package com.jvibe.gui;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatGruvboxDarkHardIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatHighContrastIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatMonokaiProIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatNordIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatSolarizedDarkIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatSolarizedLightIJTheme;
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTGitHubDarkIJTheme;
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTGitHubIJTheme;
import com.jvibe.config.ConfigService;
import com.jvibe.gui.chat.ChatCallbacks;
import com.jvibe.gui.chat.DesktopChatController;
import com.jvibe.gui.chat.DesktopChatFactory;
import com.jvibe.gui.jdbc.JdbcPanelFactory;
import com.jvibe.gui.projects.ProjectsPanelFactory;
import com.jvibe.gui.projects.ProjectsView;
import com.jvibe.gui.settings.SettingsPanelFactory;
import com.jvibe.gui.ssh.RemoteSshPanelFactory;
import com.jvibe.logging.FileLogService;
import io.avaje.inject.BeanScope;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.*;

@Singleton
public final class JvibeGui {
  private final ConfigService config;
  private final FileLogService log;
  private final MainTabsController mainTabs;
  private final DesktopChatFactory chats;
  private final ProjectsPanelFactory builds;
  private final RemoteSshPanelFactory servers;
  private final JdbcPanelFactory databases;
  private final SettingsPanelFactory settings;
  private DesktopChatController chat;
  private ProjectsView build;
  private JFrame frame;

  public JvibeGui(@External ConfigService config, @External FileLogService log,
      MainTabsController mainTabs, DesktopChatFactory chats, ProjectsPanelFactory builds,
      RemoteSshPanelFactory servers, JdbcPanelFactory databases, SettingsPanelFactory settings) {
    this.config = config;
    this.log = log;
    this.mainTabs = mainTabs;
    this.chats = chats;
    this.builds = builds;
    this.servers = servers;
    this.databases = databases;
    this.settings = settings;
  }

  public static void main(String[] args) {
    System.setProperty("apple.awt.application.name", "JVibe");
    System.setProperty("flatlaf.useNativeLibrary", "false");
    SwingUtilities.invokeLater(() -> {
      var scope = BeanScope.builder().build();
      try { scope.get(JvibeGui.class).start(scope); }
      catch (Exception e) {
        scope.close();
        JOptionPane.showMessageDialog(null, e.getMessage(), "JVibe startup failed", JOptionPane.ERROR_MESSAGE);
      }
    });
  }

  private void start(BeanScope scope) throws Exception {
    config.bootstrap();
    applyTheme(AppTheme.fromConfig(config.config().theme()));
    frame = new JFrame("JVibe");
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    frame.addWindowListener(new java.awt.event.WindowAdapter() {
      @Override public void windowClosed(java.awt.event.WindowEvent e) {
        if (chat != null) chat.close();
        if (build != null) build.close();
        scope.close();
      }
    });
    frame.setMinimumSize(new Dimension(1040, 680));
    frame.setSize(1320, 860);
    frame.setContentPane(buildRoot());
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
    chat.focusPrompt();
    SwingUtilities.invokeLater(() -> settings.signInIfNeeded(frame.getRootPane()));
  }

  JComponent buildRoot() {
    ChatCallbacks callbacks = log::event;
    chat = chats.create(callbacks);
    build = builds.create(Path.of(System.getProperty("user.dir")), callbacks, message -> log.event("INFO", message));
    return mainTabs.create(List.of(
        new MainTabsController.MainTab("chat", "Chat", chat.component()),
        new MainTabsController.MainTab("build", "Build", build.component()),
        new MainTabsController.MainTab("servers", "Servers", servers.create(callbacks)),
        new MainTabsController.MainTab("databases", "Databases", databases.create(callbacks)),
        new MainTabsController.MainTab("jvibe", "JVibe", settings.create(themeSelector()))));
  }

  private JComponent themeSelector() {
    var selector = new JComboBox<>(AppTheme.values());
    try { selector.setSelectedItem(AppTheme.fromConfig(config.config().theme())); }
    catch (java.io.IOException e) { selector.setSelectedItem(AppTheme.DRACULA); }
    selector.addActionListener(event -> {
      var selected = (AppTheme) selector.getSelectedItem();
      if (selected == null) return;
      try {
        config.updateConfig(existing -> existing.withTheme(selected.name()));
        applyTheme(selected);
        if (frame != null) SwingUtilities.updateComponentTreeUI(frame);
      } catch (java.io.IOException e) {
        JOptionPane.showMessageDialog(frame, e.getMessage(), "Unable to save theme", JOptionPane.ERROR_MESSAGE);
      }
    });
    return selector;
  }

  private static void applyTheme(AppTheme theme) {
    try { UIManager.setLookAndFeel(theme.create()); }
    catch (Exception e) { FlatDarkLaf.setup(); }
  }

  enum AppTheme {
    FLATLAF_DARK("FlatLaf Dark", FlatDarkLaf::new),
    FLATLAF_LIGHT("FlatLaf Light", FlatLightLaf::new),
    INTELLIJ_LIGHT("IntelliJ Light", FlatIntelliJLaf::new),
    DARCULA("Darcula", FlatDarculaLaf::new),
    ARC_DARK("Arc Dark", FlatArcDarkIJTheme::new),
    DRACULA("Dracula", FlatDraculaIJTheme::new),
    GRUVBOX_DARK("Gruvbox Dark", FlatGruvboxDarkHardIJTheme::new),
    MONOKAI_PRO("Monokai Pro", FlatMonokaiProIJTheme::new),
    NORD("Nord", FlatNordIJTheme::new),
    ONE_DARK("One Dark", FlatOneDarkIJTheme::new),
    SOLARIZED_DARK("Solarized Dark", FlatSolarizedDarkIJTheme::new),
    SOLARIZED_LIGHT("Solarized Light", FlatSolarizedLightIJTheme::new),
    GITHUB_DARK("GitHub Dark", FlatMTGitHubDarkIJTheme::new),
    GITHUB_LIGHT("GitHub Light", FlatMTGitHubIJTheme::new),
    HIGH_CONTRAST("High Contrast", FlatHighContrastIJTheme::new);

    private final String label;
    private final Supplier<javax.swing.LookAndFeel> supplier;

    AppTheme(String label, Supplier<javax.swing.LookAndFeel> supplier) {
      this.label = label;
      this.supplier = supplier;
    }

    javax.swing.LookAndFeel create() {
      return supplier.get();
    }

    static AppTheme fromConfig(String value) {
      if (value != null) {
        try {
          return AppTheme.valueOf(value);
        } catch (IllegalArgumentException ignored) {
          // Fall through to the default theme.
        }
      }
      return DRACULA;
    }

    @Override
    public String toString() {
      return label;
    }
  }
}
