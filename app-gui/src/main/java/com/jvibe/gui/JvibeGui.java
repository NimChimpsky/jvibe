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
import com.jvibe.gui.analysis.JfrPanelFactory;
import com.jvibe.gui.analysis.StacktracePanelFactory;
import com.jvibe.gui.chat.DesktopChatController;
import com.jvibe.gui.chat.DesktopChatFactory;
import com.jvibe.gui.controller.ProviderSettingsController;
import com.jvibe.gui.files.FileSystemTree;
import com.jvibe.gui.log.GuiLogPanel;
import com.jvibe.gui.orchestrator.AgentOrchestratorContext;
import com.jvibe.gui.orchestrator.AgentOrchestratorPanelFactory;
import com.jvibe.gui.output.OutputPanel;
import com.jvibe.gui.settings.GeneralSettingsDialog;
import com.jvibe.gui.settings.ProviderSettingsDialog;
import com.jvibe.gui.ssh.RemoteSshPanelFactory;
import com.jvibe.gui.tools.DynamicToolsPanel;
import com.jvibe.logging.FileLogService;
import com.jvibe.session.AgentSession;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.tool.ToolRegistry;
import com.jvibe.tool.dynamic.DynamicToolManagementTools;
import com.jvibe.workspace.Workspace;
import io.avaje.inject.BeanScope;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

@Singleton
public final class JvibeGui {
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
  private final ConfigService configService;
  private final ProviderSettingsController providerSettingsController;
  private final MainTabsController mainTabsController;
  private final RemoteSshPanelFactory remoteSshPanelFactory;
  private final AgentOrchestratorPanelFactory agentOrchestratorPanelFactory;
  private final JfrPanelFactory jfrPanelFactory;
  private final StacktracePanelFactory stacktracePanelFactory;
  private final FileLogService fileLogService;
  private final DynamicToolManagementTools dynamicToolManagement;
  private final DesktopChatFactory desktopChatFactory;
  private Workspace workspace;
  private JFrame frame;
  private DesktopChatController desktopChat;
  private GuiLogPanel logPanel;
  private OutputPanel outputPanel;
  private DefaultListModel<String> toolsModel;
  private DynamicToolsPanel dynamicToolsPanel;
  private AppTheme currentTheme = AppTheme.DRACULA;

  public JvibeGui(
      @External ConfigService configService,
      ProviderSettingsController providerSettingsController,
      MainTabsController mainTabsController,
      RemoteSshPanelFactory remoteSshPanelFactory,
      AgentOrchestratorPanelFactory agentOrchestratorPanelFactory,
      JfrPanelFactory jfrPanelFactory,
      StacktracePanelFactory stacktracePanelFactory,
      @External FileLogService fileLogService,
      @External DynamicToolManagementTools dynamicToolManagement,
      DesktopChatFactory desktopChatFactory) {
    this.configService = configService;
    this.providerSettingsController = providerSettingsController;
    this.mainTabsController = mainTabsController;
    this.remoteSshPanelFactory = remoteSshPanelFactory;
    this.agentOrchestratorPanelFactory = agentOrchestratorPanelFactory;
    this.jfrPanelFactory = jfrPanelFactory;
    this.stacktracePanelFactory = stacktracePanelFactory;
    this.fileLogService = fileLogService;
    this.dynamicToolManagement = dynamicToolManagement;
    this.desktopChatFactory = desktopChatFactory;
  }

  public static void main(String[] args) {
    configureApplication();
    SwingUtilities.invokeLater(() -> {
      var scope = BeanScope.builder().build();
      try {
        scope.get(JvibeGui.class).start(scope);
      } catch (Exception e) {
        scope.close();
        JOptionPane.showMessageDialog(null, e.getMessage(), "JVibe startup failed", JOptionPane.ERROR_MESSAGE);
      }
    });
  }

  private static void configureApplication() {
    System.setProperty("apple.awt.application.name", "JVibe");
    System.setProperty("apple.laf.useScreenMenuBar", "true");
    System.setProperty("flatlaf.useNativeLibrary", "false");
    applyTheme(AppTheme.DRACULA);
  }

  private static void applyTheme(AppTheme theme) {
    try {
      UIManager.setLookAndFeel(theme.create());
    } catch (Exception e) {
      FlatDarkLaf.setup();
    }
  }

  private void start(BeanScope scope) throws Exception {
    configService.bootstrap();
    currentTheme = AppTheme.fromConfig(configService.config().theme());
    applyRunningTheme(currentTheme);
    initialiseCore(Path.of(System.getProperty("user.dir")));

    frame = new JFrame("JVibe");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    var closed = new java.util.concurrent.atomic.AtomicBoolean();
    frame.addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent event) {
        if (desktopChat != null) desktopChat.close();
        closeScope(scope, closed);
      }

      @Override
      public void windowClosed(java.awt.event.WindowEvent event) {
        closeScope(scope, closed);
      }
    });
    frame.setMinimumSize(new Dimension(1040, 680));
    frame.setSize(1320, 860);
    frame.setContentPane(buildRoot());
    log("INFO", "GUI ready: " + workspace.root());
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
    desktopChat.focusPrompt();
  }

  private static void closeScope(BeanScope scope, java.util.concurrent.atomic.AtomicBoolean closed) {
    if (closed.compareAndSet(false, true)) {
      scope.close();
    }
  }

  private void initialiseCore(Path root) throws Exception {
    workspace = new Workspace(root);
    refreshTools();
  }

  private JComponent buildRoot() {
    return mainTabsController.create(List.of(
        new MainTabsController.MainTab("desktop", "Desktop", buildDashboard()),
        new MainTabsController.MainTab("remote-ssh", "Remote SSH", remoteSshPanelFactory.create(message -> log("INFO", message))),
        new MainTabsController.MainTab(
            "agent-orchestrator",
            "Agent Orchestrator",
            agentOrchestratorPanelFactory.create(frame, this::agentOrchestratorContext, message -> log("INFO", message))),
        new MainTabsController.MainTab("jfr", "JFR", jfrPanelFactory.create()),
        new MainTabsController.MainTab("stacktrace", "Stacktrace", stacktracePanelFactory.create())));
  }

  private JComponent buildDashboard() {
    var mainAndRight = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildMain(), buildInspector());
    mainAndRight.setResizeWeight(1.0);
    mainAndRight.setDividerSize(6);
    mainAndRight.setBorder(null);

    var root = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildSidebar(), mainAndRight);
    root.setResizeWeight(0.0);
    root.setDividerSize(6);
    root.setBorder(null);
    return root;
  }

  private AgentOrchestratorContext agentOrchestratorContext() {
    var session = desktopChat == null ? null : desktopChat.activeSession();
    var provider = session == null ? "jvibe" : session.provider();
    var model = session == null ? "jvibe-coder" : session.model();
    var deploymentWorkspace = workspace.root();
    if (session != null && session.mode() == AgentSession.Mode.WORKSPACE) {
      deploymentWorkspace = desktopChat.activeWorkingDirectory();
    }
    return new AgentOrchestratorContext(deploymentWorkspace, provider, model);
  }

  private JComponent buildSidebar() {
    var files = new JPanel(new BorderLayout(0, 8));
    files.setBorder(new EmptyBorder(8, 16, 8, 16));
    files.add(section("Files"), BorderLayout.NORTH);
    files.add(scroll(FileSystemTree.create(
        workspace.root(),
        new FileSystemTree.Actions(desktopChat::addFileReference, desktopChat::createWorkspace))), BorderLayout.CENTER);

    var bottom = new JPanel();
    bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
    bottom.setBorder(new EmptyBorder(12, 16, 16, 16));
    bottom.setMinimumSize(new Dimension(260, 120));
    bottom.setPreferredSize(new Dimension(260, DesktopChatController.COMPOSER_HEIGHT));
    bottom.add(section("Settings"));
    bottom.add(Box.createVerticalStrut(8));
    var settingsActions = new JPanel(new java.awt.GridLayout(1, 2, 8, 0));
    settingsActions.setAlignmentX(Component.LEFT_ALIGNMENT);
    settingsActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
    settingsActions.add(button("General", event -> showGeneralSettingsDialog()));
    settingsActions.add(button("Providers", event -> showSettingsDialog()));
    bottom.add(settingsActions);
    bottom.add(Box.createVerticalStrut(8));
    bottom.add(themeSelector());

    files.setMinimumSize(new Dimension(260, 140));

    var sections = new JSplitPane(JSplitPane.VERTICAL_SPLIT, files, bottom);
    sections.setResizeWeight(1.0);
    sections.setDividerSize(6);
    sections.setBorder(null);

    var panel = new JPanel(new BorderLayout());
    panel.setPreferredSize(new Dimension(260, 0));
    panel.add(sections, BorderLayout.CENTER);
    return panel;
  }

  private JComponent buildMain() {
    desktopChat = desktopChatFactory.create(new DesktopChatController.Callbacks() {
      @Override public void log(String level, String message) { JvibeGui.this.log(level, message); }
      @Override public void outputChanged() { refreshOutput(); }
      @Override public void fullLogChanged() { refreshFullLog(); }
      @Override public void activeSessionChanged() { refreshTools(); }
    });
    return desktopChat.component();
  }

  private JComponent buildInspector() {
    if (toolsModel == null) {
      toolsModel = new DefaultListModel<>();
    }
    logPanel = new GuiLogPanel();

    var refresh = button("Refresh");
    refresh.addActionListener(event -> {
      refreshTools();
      refreshFullLog();
      refreshOutput();
      log("INFO", "Refreshed tools");
    });

    var header = new JPanel(new BorderLayout());
    header.add(sectionLabel("Tools"), BorderLayout.WEST);
    header.add(refresh, BorderLayout.EAST);

    dynamicToolsPanel = new DynamicToolsPanel(
        dynamicToolManagement,
        this::activeWorkspace,
        this::activeToolRegistry,
        this::log);
    var toolViews = new JSplitPane(
        JSplitPane.VERTICAL_SPLIT,
        dynamicToolsPanel,
        scroll(list(toolsModel)));
    toolViews.setResizeWeight(0.62);
    toolViews.setDividerSize(6);
    toolViews.setBorder(null);

    var toolsPanel = new JPanel(new BorderLayout(0, 10));
    toolsPanel.add(header, BorderLayout.NORTH);
    toolsPanel.add(toolViews, BorderLayout.CENTER);

    var bottomTabs = new JTabbedPane();
    bottomTabs.addTab("Log", scroll(logPanel.logList()));
    bottomTabs.addTab("Tools", toolsPanel);
    bottomTabs.addTab("Full Log", scroll(logPanel.fullLogArea()));
    refreshFullLog();

    var bottom = new JPanel(new BorderLayout(0, 10));
    bottom.setBorder(new EmptyBorder(8, 12, 12, 12));
    bottom.setMinimumSize(new Dimension(360, 0));
    bottom.add(bottomTabs, BorderLayout.CENTER);

    outputPanel = new OutputPanel(configService.paths().tmp(), this::log);
    outputPanel.refresh();
    var outputWrapper = new JPanel(new BorderLayout());
    outputWrapper.setBorder(new EmptyBorder(8, 12, 12, 12));
    outputWrapper.add(outputPanel, BorderLayout.CENTER);

    var topTabs = new JTabbedPane();
    topTabs.addTab("File Output", outputWrapper);

    var top = new JPanel(new BorderLayout());
    top.setMinimumSize(new Dimension(360, 0));
    top.add(topTabs, BorderLayout.CENTER);

    var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
    split.setResizeWeight(0.46);
    split.setDividerSize(6);
    split.setBorder(null);

    var panel = new JPanel(new BorderLayout());
    panel.setPreferredSize(new Dimension(360, 0));
    panel.add(split, BorderLayout.CENTER);
    refreshTools();
    return panel;
  }

  private void showSettingsDialog() {
    new ProviderSettingsDialog(frame, providerSettingsController, desktopChat::refreshModelSelectors, message -> log("INFO", message)).show();
  }

  private void showGeneralSettingsDialog() {
    new GeneralSettingsDialog(frame, configService, message -> log("INFO", message)).show();
  }

  private JComponent themeSelector() {
    var themes = new JComboBox<>(AppTheme.values());
    themes.setSelectedItem(currentTheme);
    themes.setAlignmentX(Component.LEFT_ALIGNMENT);
    themes.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
    themes.addActionListener(event -> {
      var selected = (AppTheme) themes.getSelectedItem();
      if (selected == null || selected == currentTheme) {
        return;
      }
      applyRunningTheme(selected);
      saveTheme(selected);
      log("INFO", "Applied theme: " + selected);
    });
    return themes;
  }

  private void saveTheme(AppTheme theme) {
    try {
      configService.updateConfig(existing -> existing.withTheme(theme.name()));
    } catch (IOException e) {
      log("ERROR", "Unable to save theme: " + e.getMessage());
    }
  }

  private void refreshTools() {
    if (desktopChat == null || desktopChat.activeSession() == null) {
      return;
    }
    if (toolsModel == null) {
      toolsModel = new DefaultListModel<>();
    } else {
      toolsModel.clear();
    }
    desktopChat.activeToolRegistry().definitions().stream()
        .sorted(Comparator.comparing(ToolDefinition::id))
        .map(tool -> tool.id() + "  " + tool.description())
        .forEach(toolsModel::addElement);
    if (dynamicToolsPanel != null) {
      dynamicToolsPanel.refresh();
    }
  }

  private Workspace activeWorkspace() {
    return desktopChat == null ? workspace : desktopChat.activeWorkspace();
  }

  private ToolRegistry activeToolRegistry() {
    return desktopChat == null ? new ToolRegistry() : desktopChat.activeToolRegistry();
  }

  private JButton button(String text) {
    var button = new JButton(text);
    button.setAlignmentX(Component.LEFT_ALIGNMENT);
    return button;
  }

  private JButton button(String text, java.awt.event.ActionListener listener) {
    var button = button(text);
    button.addActionListener(listener);
    return button;
  }

  private JLabel label(String text, int size, int style) {
    var label = new JLabel(text);
    label.setFont(label.getFont().deriveFont(style, size));
    return label;
  }

  private JComponent section(String text) {
    var panel = new JPanel(new BorderLayout());
    panel.setAlignmentX(Component.LEFT_ALIGNMENT);
    panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
    panel.add(sectionLabel(text), BorderLayout.WEST);
    return panel;
  }

  private JLabel sectionLabel(String text) {
    var label = label(text.toUpperCase(), 11, Font.BOLD);
    label.setForeground(UIManager.getColor("Label.disabledForeground"));
    return label;
  }

  private JList<String> list(DefaultListModel<String> model) {
    var list = new JList<>(model);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    return list;
  }

  private JScrollPane scroll(Component component) {
    var scroll = new JScrollPane(component);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    return scroll;
  }

  private void log(String level, String message) {
    if (fileLogService != null) {
      fileLogService.event(level, message);
      refreshFullLog();
    }
    if (logPanel != null) {
      logPanel.append(timestamp(), level, message);
    }
  }

  private void refreshFullLog() {
    if (logPanel == null || fileLogService == null) {
      return;
    }
    logPanel.setFullLog(fileLogService.readAll());
  }

  private void refreshOutput() {
    if (outputPanel != null) {
      outputPanel.refresh();
    }
  }

  private String timestamp() {
    return LocalTime.now().format(TIME);
  }

  private void applyRunningTheme(AppTheme theme) {
    currentTheme = theme;
    applyTheme(theme);
    if (frame != null) {
      SwingUtilities.updateComponentTreeUI(frame);
    }
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
