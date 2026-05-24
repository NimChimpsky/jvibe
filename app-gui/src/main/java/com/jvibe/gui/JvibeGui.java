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
import com.jvibe.config.JvibeConfig;
import com.jvibe.events.AgentEvent;
import com.jvibe.gui.chat.ChatTranscript;
import com.jvibe.gui.controller.ModelSelectionController;
import com.jvibe.gui.controller.PromptController;
import com.jvibe.gui.controller.ProviderSettingsController;
import com.jvibe.gui.swarm.AgentSwarmContext;
import com.jvibe.gui.swarm.AgentSwarmPanelFactory;
import com.jvibe.gui.files.FileSystemTree;
import com.jvibe.gui.log.GuiLogPanel;
import com.jvibe.gui.output.OutputPanel;
import com.jvibe.gui.settings.GeneralSettingsDialog;
import com.jvibe.gui.settings.ProviderSettingsDialog;
import com.jvibe.gui.terminal.ShellPanel;
import com.jvibe.logging.FileLogService;
import com.jvibe.session.AgentSession;
import com.jvibe.session.SessionFactory;
import com.jvibe.tool.ToolDefinition;
import com.jvibe.workspace.Workspace;
import io.avaje.inject.BeanScope;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

public final class JvibeGui {
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
  static final int COMPOSER_HEIGHT = 220;
  static final int PROMPT_INPUT_HEIGHT = 118;
  static final int FILE_REFERENCE_ROW_HEIGHT = 26;
  private static final int SHELL_HEIGHT = 220;

  private BeanScope scope;
  private ConfigService configService;
  private ProviderSettingsController providerSettingsController;
  private ModelSelectionController modelSelectionController;
  private PromptController promptController;
  private AgentSwarmPanelFactory agentSwarmPanelFactory;
  private FileLogService fileLogService;
  private SessionFactory sessionFactory;
  private Workspace workspace;
  private JFrame frame;
  private JTabbedPane chatTabs;
  private ChatTabState activeTab;
  private final Map<Path, ChatTabState> workspaceTabStates = new LinkedHashMap<>();
  private int chatSequence;
  private JPanel content;
  private GuiLogPanel logPanel;
  private OutputPanel outputPanel;
  private DefaultListModel<String> toolsModel;
  private AppTheme currentTheme = AppTheme.FLATLAF_DARK;

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      configureApplication();
      try {
        new JvibeGui().start();
      } catch (Exception e) {
        JOptionPane.showMessageDialog(null, e.getMessage(), "JVibe startup failed", JOptionPane.ERROR_MESSAGE);
      }
    });
  }

  private static void configureApplication() {
    System.setProperty("apple.awt.application.name", "JVibe");
    System.setProperty("apple.laf.useScreenMenuBar", "true");
    System.setProperty("flatlaf.useNativeLibrary", "false");
    applyTheme(AppTheme.FLATLAF_DARK);
  }

  private static void applyTheme(AppTheme theme) {
    try {
      UIManager.setLookAndFeel(theme.create());
    } catch (Exception e) {
      FlatDarkLaf.setup();
    }
  }

  private void start() throws Exception {
    scope = BeanScope.builder().build();
    configService = scope.get(ConfigService.class);
    providerSettingsController = scope.get(ProviderSettingsController.class);
    modelSelectionController = scope.get(ModelSelectionController.class);
    promptController = scope.get(PromptController.class);
    agentSwarmPanelFactory = scope.get(AgentSwarmPanelFactory.class);
    fileLogService = scope.get(FileLogService.class);
    sessionFactory = scope.get(SessionFactory.class);
    configService.bootstrap();
    currentTheme = AppTheme.fromConfig(configService.config().theme());
    applyRunningTheme(currentTheme);
    initialiseCore(Path.of(System.getProperty("user.dir")));

    frame = new JFrame("JVibe");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent event) {
        if (chatTabs != null) {
          for (var index = 0; index < chatTabs.getTabCount(); index++) {
            if (chatTabs.getComponentAt(index) instanceof JComponent component
                && component.getClientProperty("state") instanceof ChatTabState state
                && state.shellPanel != null) {
              state.shellPanel.stopSession();
            }
          }
        }
      }

      @Override
      public void windowClosed(java.awt.event.WindowEvent event) {
        scope.close();
      }
    });
    frame.setMinimumSize(new Dimension(1040, 680));
    frame.setSize(1320, 860);
    frame.setContentPane(buildRoot());
    showChatView();
    log("INFO", "GUI ready: " + workspace.root());
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
    SwingUtilities.invokeLater(() -> activeTab.prompt.requestFocusInWindow());
  }

  private void initialiseCore(Path root) throws Exception {
    workspace = new Workspace(root);
    refreshTools();
  }

  private JComponent buildRoot() {
    var rootTabs = new JTabbedPane();
    rootTabs.addTab("Desktop", buildDashboard());
    rootTabs.addTab("Agent Orchestrator", agentSwarmPanelFactory.create(frame, this::agentSwarmContext, message -> log("INFO", message)));
    return rootTabs;
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

  private AgentSwarmContext agentSwarmContext() {
    var provider = activeTab == null || activeTab.session == null ? "local" : activeTab.session.provider();
    var model = activeTab == null || activeTab.session == null ? "echo" : activeTab.session.model();
    var deploymentWorkspace = workspace.root();
    if (activeTab != null && activeTab.session != null && activeTab.session.mode() == AgentSession.Mode.WORKSPACE) {
      deploymentWorkspace = activeTab.shellWorkingDirectory;
    }
    return new AgentSwarmContext(deploymentWorkspace, provider, model);
  }

  private JComponent buildSidebar() {
    var files = new JPanel(new BorderLayout(0, 8));
    files.setBorder(new EmptyBorder(8, 16, 8, 16));
    files.add(section("Files"), BorderLayout.NORTH);
    files.add(scroll(FileSystemTree.create(
        workspace.root(),
        new FileSystemTree.Actions(this::addFileReference, this::createWorkspaceTab))), BorderLayout.CENTER);

    var bottom = new JPanel();
    bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
    bottom.setBorder(new EmptyBorder(12, 16, 16, 16));
    bottom.setMinimumSize(new Dimension(260, 120));
    bottom.setPreferredSize(new Dimension(260, COMPOSER_HEIGHT));
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
    var panel = new JPanel(new BorderLayout());
    content = new JPanel(new BorderLayout());
    panel.add(content, BorderLayout.CENTER);
    return panel;
  }

  private void refreshModelSelector() {
    if (activeTab == null) {
      return;
    }
    refreshModelSelector(activeTab);
  }

  private void refreshModelSelectors() {
    if (chatTabs == null) {
      return;
    }
    for (var index = 0; index < chatTabs.getTabCount(); index++) {
      if (chatTabs.getComponentAt(index) instanceof JComponent component
          && component.getClientProperty("state") instanceof ChatTabState state) {
        refreshModelSelector(state);
      }
    }
  }

  private void refreshModelSelector(ChatTabState state) {
    if (state.modelSelector == null || modelSelectionController == null) {
      return;
    }
    var current = state.session == null ? "" : state.session.provider() + "/" + state.session.model();
    state.modelSelectorRefreshing = true;
    try {
      state.modelSelector.removeAllItems();
      var containsCurrent = false;
      for (var choice : modelSelectionController.choices()) {
        var display = choice.display();
        state.modelSelector.addItem(display);
        containsCurrent = containsCurrent || display.equals(current);
      }
      if (containsCurrent) {
        state.modelSelector.setSelectedItem(current);
      }
      if (!containsCurrent && state.modelSelector.getItemCount() > 0) {
        state.modelSelector.setSelectedIndex(0);
        applySelectedModel(state, false);
      } else if (state.modelSelector.getItemCount() == 0 && state.session != null) {
        state.session.selectModel("", "");
      }
    } finally {
      state.modelSelectorRefreshing = false;
    }
  }

  private void applySelectedModel() {
    if (activeTab != null) {
      applySelectedModel(activeTab);
    }
  }

  private void applySelectedModel(ChatTabState state) {
    applySelectedModel(state, !state.modelSelectorRefreshing);
  }

  private void applySelectedModel(ChatTabState state, boolean logChange) {
    if (state.session == null || state.modelSelector == null || state.modelSelector.getSelectedItem() == null) {
      return;
    }
    var selected = state.modelSelector.getSelectedItem().toString();
    var previous = state.session.provider() + "/" + state.session.model();
    modelSelectionController.apply(state.session, selected);
    if (logChange && !selected.equals(previous)) {
      log("INFO", "Selected model: " + selected);
    }
  }

  JComponent buildComposer() {
    return buildComposer(new ChatTabState(null, Path.of("."), new ArrayList<>()));
  }

  private JComponent buildComposer(ChatTabState state) {
    state.prompt = new JTextArea(4, 20);
    state.prompt.setLineWrap(true);
    state.prompt.setWrapStyleWord(true);
    state.prompt.setMargin(new Insets(10, 10, 10, 10));
    state.prompt.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sendPrompt");
    state.prompt.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "insert-break");
    state.prompt.getActionMap().put("sendPrompt", new javax.swing.AbstractAction() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent event) {
        sendPrompt(state);
      }
    });

    var send = button("Send");
    send.addActionListener(event -> sendPrompt(state));

    state.modelSelector = new JComboBox<>();
    state.modelSelector.addActionListener(event -> applySelectedModel(state));
    refreshModelSelector(state);

    var modelPanel = new JPanel();
    modelPanel.add(new JLabel("Model"));
    modelPanel.add(state.modelSelector);

    var footer = new JPanel(new BorderLayout(10, 0));
    footer.add(modelPanel, BorderLayout.WEST);
    footer.add(send, BorderLayout.EAST);

    var terminalRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
    terminalRow.add(shellToggleButton(state));

    var controls = new JPanel(new BorderLayout(0, 6));
    controls.add(footer, BorderLayout.NORTH);
    controls.add(terminalRow, BorderLayout.SOUTH);

    state.fileReferencePanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 1));
    state.fileReferencePanel.setOpaque(false);
    var fileReferenceRowSize = new Dimension(0, FILE_REFERENCE_ROW_HEIGHT);
    state.fileReferencePanel.setMinimumSize(fileReferenceRowSize);
    state.fileReferencePanel.setPreferredSize(fileReferenceRowSize);
    state.fileReferencePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, FILE_REFERENCE_ROW_HEIGHT));

    var promptStack = new JPanel(new BorderLayout(0, 4));
    promptStack.add(state.fileReferencePanel, BorderLayout.NORTH);
    promptStack.add(fixedPromptScroll(state.prompt), BorderLayout.CENTER);

    var panel = new JPanel(new BorderLayout(10, 0));
    panel.setBorder(new EmptyBorder(12, 12, 12, 12));
    panel.setPreferredSize(new Dimension(0, COMPOSER_HEIGHT));
    panel.setMinimumSize(new Dimension(0, COMPOSER_HEIGHT));
    panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, COMPOSER_HEIGHT));
    panel.add(promptStack, BorderLayout.CENTER);
    panel.add(controls, BorderLayout.SOUTH);
    return panel;
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

    var toolsPanel = new JPanel(new BorderLayout(0, 10));
    toolsPanel.add(header, BorderLayout.NORTH);
    toolsPanel.add(scroll(list(toolsModel)), BorderLayout.CENTER);

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

  private void showChatView() {
    chatTabs = new JTabbedPane();
    chatSequence = 0;
    var defaultState = createDefaultChatTab(false);
    chatTabs.addChangeListener(event -> {
      var selected = chatTabs.getSelectedComponent();
      if (selected instanceof JComponent component && component.getClientProperty("state") instanceof ChatTabState state) {
        activeTab = state;
        refreshTools();
        refreshModelSelector(state);
        SwingUtilities.invokeLater(() -> state.prompt.requestFocusInWindow());
      }
    });
    chatTabs.setSelectedIndex(0);
    activeTab = defaultState;
    refreshTools();
    setContent(chatTabs);
  }

  private ChatTabState createDefaultChatTab(boolean select) {
    var refs = new ArrayList<Path>();
    var session = sessionFactory.createDefaultChat(configService.paths().tmp(), () -> List.copyOf(refs));
    var state = new ChatTabState(session, configService.paths().tmp(), refs);
    var number = ++chatSequence;
    addChatTab(
        number == 1 ? "Chat" : "Chat " + number,
        "No workspace write access",
        state,
        "<html>Right click to add files to context, write access only in JVibe tmp and accessible via \"File Output\".<br>Create a workspace for full in place file editing.</html>");
    if (select) {
      selectTab(state);
      log("INFO", "Started new chat");
    }
    return state;
  }

  private void addChatTab(String title, String tooltip, ChatTabState state, String note) {
    var component = buildChatTab(state, note);
    component.putClientProperty("state", state);
    chatTabs.addTab(title, component);
    var index = chatTabs.getTabCount() - 1;
    chatTabs.setToolTipTextAt(index, tooltip);
    chatTabs.setTabComponentAt(index, tabTitle(title, tooltip, state));
  }

  private JComponent tabTitle(String title, String tooltip, ChatTabState state) {
    var panel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
    panel.setOpaque(false);
    panel.setToolTipText(tooltip);

    var label = new JLabel(title);
    label.setToolTipText(tooltip);
    panel.add(label);

    if (state.session.mode() == AgentSession.Mode.CHAT) {
      panel.add(tabAction("+", "New chat", () -> createDefaultChatTab(true)));
    }

    var menu = new JPopupMenu();
    menu.add("Delete").addActionListener(event -> deleteTab(state));
    panel.setComponentPopupMenu(menu);
    label.setComponentPopupMenu(menu);
    panel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        selectTab(state);
      }
    });
    return panel;
  }

  private JLabel tabAction(String text, String tooltip, Runnable action) {
    var label = new JLabel(text);
    label.setToolTipText(tooltip);
    label.setBorder(new EmptyBorder(0, 4, 0, 4));
    label.setFocusable(false);
    label.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        action.run();
        event.consume();
      }
    });
    return label;
  }

  private void deleteTab(ChatTabState state) {
    for (var index = 0; index < chatTabs.getTabCount(); index++) {
      if (chatTabs.getComponentAt(index) instanceof JComponent component
          && component.getClientProperty("state") == state) {
        if (state.shellPanel != null) {
          state.shellPanel.stopSession();
        }
        workspaceTabStates.values().removeIf(value -> value == state);
        chatTabs.removeTabAt(index);
        if (chatTabs.getTabCount() == 0) {
          createDefaultChatTab(true);
        }
        refreshTools();
        return;
      }
    }
  }

  private JComponent buildChatTab(ChatTabState state, String note) {
    state.transcript = new ChatTranscript();
    state.shellPanel = new ShellPanel(state.shellWorkingDirectory, message -> log("INFO", message));
    state.shellPanel.setVisible(false);

    var chatContent = new JPanel(new BorderLayout());
    if (note != null && !note.isBlank()) {
      var noteLabel = new JLabel(note);
      noteLabel.setBorder(new EmptyBorder(8, 12, 4, 12));
      noteLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
      chatContent.add(noteLabel, BorderLayout.NORTH);
    }
    chatContent.add(scroll(state.transcript.component()), BorderLayout.CENTER);

    var chatAndComposer = new JPanel(new BorderLayout());
    chatAndComposer.add(chatContent, BorderLayout.CENTER);
    chatAndComposer.add(buildComposer(state), BorderLayout.SOUTH);

    state.chatAndShell = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chatAndComposer, state.shellPanel);
    state.chatAndShell.setResizeWeight(1.0);
    state.chatAndShell.setDividerSize(0);
    state.chatAndShell.setBorder(null);
    return state.chatAndShell;
  }

  private void createWorkspaceTab(Path root) {
    var normalized = root.toAbsolutePath().normalize();
    var existing = workspaceTabStates.get(normalized);
    if (existing != null) {
      selectTab(existing);
      return;
    }
    var state = new ChatTabState(sessionFactory.create(normalized), normalized, new ArrayList<>());
    workspaceTabStates.put(normalized, state);
    addChatTab(filename(normalized), normalized.toString(), state, null);
    selectTab(state);
    log("INFO", "Created workspace tab: " + normalized);
  }

  private void selectTab(ChatTabState state) {
    for (var index = 0; index < chatTabs.getTabCount(); index++) {
      if (chatTabs.getComponentAt(index) instanceof JComponent component
          && component.getClientProperty("state") == state) {
        chatTabs.setSelectedIndex(index);
        return;
      }
    }
  }

  private void toggleTerminal() {
    toggleTerminal(activeTab);
  }

  private void toggleTerminal(ChatTabState state) {
    if (state.shellPanel != null && state.shellPanel.isVisible()) {
      minimizeTerminal(state);
    } else {
      showTerminal(state);
    }
  }

  private void showTerminal() {
    showTerminal(activeTab);
  }

  private void showTerminal(ChatTabState state) {
    if (state == null || state.chatAndShell == null || state.shellPanel == null) {
      return;
    }
    state.shellPanel.setVisible(true);
    state.chatAndShell.setDividerSize(6);
    refresh(state.chatAndShell);
    SwingUtilities.invokeLater(() -> {
      var available = state.chatAndShell.getHeight();
      state.chatAndShell.setDividerLocation(Math.max(180, available - SHELL_HEIGHT));
      state.shellPanel.startOrRestore();
    });
  }

  private void minimizeTerminal() {
    minimizeTerminal(activeTab);
  }

  private void minimizeTerminal(ChatTabState state) {
    if (state == null || state.chatAndShell == null || state.shellPanel == null) {
      return;
    }
    state.shellPanel.setVisible(false);
    state.chatAndShell.setDividerSize(0);
    state.chatAndShell.setDividerLocation(1.0);
    refresh(state.chatAndShell);
    log("INFO", "Shell minimized");
  }

  private void restartTerminal() {
    restartTerminal(activeTab);
  }

  private void restartTerminal(ChatTabState state) {
    if (state == null || state.shellPanel == null) {
      return;
    }
    if (!state.shellPanel.isVisible()) {
      showTerminal(state);
    }
    state.shellPanel.restart();
    log("INFO", "Shell restart requested");
  }

  private void showSettingsDialog() {
    new ProviderSettingsDialog(frame, providerSettingsController, this::refreshModelSelectors, message -> log("INFO", message)).show();
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
      var existing = configService.config();
      configService.saveConfig(new JvibeConfig(
          existing.defaultProvider(),
          existing.defaultModel(),
          existing.maxToolIterations(),
          theme.name(),
          existing.autonomyMode(),
          existing.gatewayEnabled(),
          existing.gatewayHost(),
          existing.gatewayPort(),
          existing.deploymentProfiles()));
    } catch (IOException e) {
      log("ERROR", "Unable to save theme: " + e.getMessage());
    }
  }

  private void sendPrompt() {
    sendPrompt(activeTab);
  }

  private void sendPrompt(ChatTabState state) {
    if (state == null) {
      return;
    }
    if (state.promptRunning) {
      log("WARN", "Prompt already running");
      return;
    }
    var typedText = state.prompt.getText().trim();
    if (typedText.isEmpty() && state.promptFileReferences.isEmpty()) {
      return;
    }
    if (state.modelSelector.getSelectedItem() == null) {
      var message = "No provider model selected. Open Providers and enable a provider.";
      appendAssistant(state, "Error: " + message);
      log("ERROR", message);
      return;
    }
    var references = List.copyOf(state.promptFileReferences);
    var text = promptTextWithReferences(typedText, references);
    state.prompt.setText("");
    clearFileReferences(state);
    appendUser(state, typedText, references);
    state.transcript.finishAssistant();
    state.activeAssistantLog = new StringBuilder();
    state.activeAssistantChars = 0;
    state.promptRunning = true;
    status("Running");
    fileLogService.prompt(state.session.provider(), state.session.model(), text);
    refreshFullLog();
    log("INFO", "Prompt submitted to " + state.session.provider() + "/" + state.session.model());

    var worker = new SwingWorker<Void, AgentEvent>() {
      @Override
      protected Void doInBackground() {
        try {
          promptController.submit(state.session, text).forEach(this::publish);
        } catch (RuntimeException e) {
          publish(AgentEvent.error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        return null;
      }

      @Override
      protected void process(java.util.List<AgentEvent> events) {
        for (var event : events) {
          handleEvent(state, event);
        }
      }

      @Override
      protected void done() {
        state.promptRunning = false;
        status("Ready");
        refreshOutput();
        try {
          get();
        } catch (Exception e) {
          log("ERROR", rootMessage(e));
        }
      }
    };
    worker.execute();
  }

  private void handleEvent(ChatTabState state, AgentEvent event) {
    switch (event.type()) {
      case ASSISTANT_DELTA -> appendAssistantDelta(state, event.message());
      case TOOL_STARTED -> log("INFO", "Tool started: " + event.message());
      case TOOL_FINISHED -> {
        log("INFO", "Tool finished: " + event.message());
        refreshOutput();
      }
      case ERROR -> {
        flushAssistantLog(state);
        appendAssistant(state, "Error: " + event.message());
        log("ERROR", event.message());
      }
      case DONE -> {
        flushAssistantLog(state);
        state.transcript.finishAssistant();
        if (state.activeAssistantChars == 0) {
          log("WARN", "Run completed without assistant text");
        } else {
          log("INFO", "Run complete");
        }
      }
    }
  }

  private void refreshTools() {
    if (activeTab == null || activeTab.session == null) {
      return;
    }
    if (toolsModel == null) {
      toolsModel = new DefaultListModel<>();
    } else {
      toolsModel.clear();
    }
    activeTab.session.toolRegistry().definitions().stream()
        .sorted(Comparator.comparing(ToolDefinition::id))
        .map(tool -> tool.id() + "\n" + tool.description())
        .forEach(toolsModel::addElement);
  }

  private void appendUser(String text) {
    appendUser(activeTab, text, List.of());
  }

  private void appendUser(ChatTabState state, String text, List<Path> references) {
    state.transcript.appendUser(text, references);
  }

  private void appendAssistant(ChatTabState state, String text) {
    state.transcript.appendAssistant(text);
  }

  private void appendAssistantDelta(ChatTabState state, String text) {
    if (!state.transcript.appendAssistantDelta(text)) {
      return;
    }
    state.activeAssistantLog.append(text);
    state.activeAssistantChars += text.length();
  }

  private void flushAssistantLog(ChatTabState state) {
    if (fileLogService != null && !state.activeAssistantLog.isEmpty()) {
      fileLogService.assistant(state.session.provider(), state.session.model(), state.activeAssistantLog.toString());
      state.activeAssistantLog.setLength(0);
      refreshFullLog();
    }
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

  static JButton terminalButton(java.awt.event.ActionListener listener) {
    var button = new JButton(new TerminalIcon());
    button.setToolTipText("Open terminal");
    button.setFocusable(false);
    button.setPreferredSize(new Dimension(32, 28));
    button.setMinimumSize(new Dimension(32, 28));
    button.addActionListener(listener);
    return button;
  }

  JButton shellToggleButton() {
    return shellToggleButton(activeTab);
  }

  JButton shellToggleButton(ChatTabState state) {
    var button = terminalButton(event -> toggleTerminal(state));
    var menu = new JPopupMenu();
    var restart = menu.add("Restart Shell");
    restart.addActionListener(event -> restartTerminal(state));
    button.setComponentPopupMenu(menu);
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

  private JScrollPane fixedPromptScroll(Component component) {
    var scroll = scroll(component);
    var size = new Dimension(0, PROMPT_INPUT_HEIGHT);
    scroll.setMinimumSize(size);
    scroll.setPreferredSize(size);
    scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, PROMPT_INPUT_HEIGHT));
    return scroll;
  }

  private void addFileReference(Path path) {
    if (activeTab == null) {
      return;
    }
    var normalized = path.toAbsolutePath().normalize();
    if (activeTab.promptFileReferences.contains(normalized)) {
      activeTab.prompt.requestFocusInWindow();
      return;
    }
    activeTab.promptFileReferences.add(normalized);
    refreshFileReferences(activeTab);
    activeTab.prompt.requestFocusInWindow();
    log("INFO", "Added file reference: " + normalized);
  }

  private void clearFileReferences(ChatTabState state) {
    state.promptFileReferences.clear();
    refreshFileReferences(state);
  }

  private void removeFileReference(Path path) {
    if (activeTab == null) {
      return;
    }
    activeTab.promptFileReferences.remove(path);
    refreshFileReferences(activeTab);
    activeTab.prompt.requestFocusInWindow();
  }

  private void refreshFileReferences(ChatTabState state) {
    if (state.fileReferencePanel == null) {
      return;
    }
    state.fileReferencePanel.removeAll();
    for (var path : state.promptFileReferences) {
      state.fileReferencePanel.add(fileReferenceChip(path));
    }
    refresh(state.fileReferencePanel);
  }

  JButton fileReferenceChip(Path path) {
    var button = new JButton(filename(path) + " x");
    button.setToolTipText(path.toString());
    button.setFocusable(false);
    button.setMargin(new Insets(2, 8, 2, 8));
    button.addActionListener(event -> removeFileReference(path));
    return button;
  }

  private String promptTextWithReferences(String text, List<Path> references) {
    if (references.isEmpty()) {
      return text;
    }
    var builder = new StringBuilder("File references:\n");
    for (var path : references) {
      builder.append("- ").append(filename(path)).append(": ").append(path).append('\n');
    }
    if (!text.isBlank()) {
      builder.append('\n').append(text);
    }
    return builder.toString();
  }

  private String filename(Path path) {
    var name = path.getFileName();
    return name == null ? path.toString() : name.toString();
  }

  private void setContent(JComponent component) {
    content.removeAll();
    content.add(component, BorderLayout.CENTER);
    refresh(content);
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

  private void status(String message) {
    if (message != null && !message.isBlank()) {
      log("INFO", "Status: " + message);
    }
  }

  private String timestamp() {
    return LocalTime.now().format(TIME);
  }

  private void refresh(JComponent component) {
    component.revalidate();
    component.repaint();
  }

  private void applyRunningTheme(AppTheme theme) {
    currentTheme = theme;
    applyTheme(theme);
    if (frame != null) {
      SwingUtilities.updateComponentTreeUI(frame);
    }
  }

  private static String rootMessage(Exception e) {
    var cause = e.getCause() == null ? e : e.getCause();
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }

  private static final class ChatTabState {
    private final AgentSession session;
    private final Path shellWorkingDirectory;
    private final List<Path> promptFileReferences;
    private ChatTranscript transcript;
    private JSplitPane chatAndShell;
    private ShellPanel shellPanel;
    private JComboBox<String> modelSelector;
    private boolean modelSelectorRefreshing;
    private JTextArea prompt;
    private JPanel fileReferencePanel;
    private StringBuilder activeAssistantLog = new StringBuilder();
    private int activeAssistantChars;
    private boolean promptRunning;

    private ChatTabState(AgentSession session, Path shellWorkingDirectory, List<Path> promptFileReferences) {
      this.session = session;
      this.shellWorkingDirectory = shellWorkingDirectory;
      this.promptFileReferences = promptFileReferences;
    }
  }

  private static final class TerminalIcon implements Icon {
    private static final int WIDTH = 16;
    private static final int HEIGHT = 14;

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
      var g = (Graphics2D) graphics.create();
      try {
        g.setColor(component.getForeground());
        g.drawRoundRect(x, y + 1, WIDTH - 1, HEIGHT - 2, 3, 3);
        g.drawLine(x + 4, y + 5, x + 7, y + 7);
        g.drawLine(x + 7, y + 7, x + 4, y + 9);
        g.drawLine(x + 9, y + 10, x + 13, y + 10);
      } finally {
        g.dispose();
      }
    }

    @Override
    public int getIconWidth() {
      return WIDTH;
    }

    @Override
    public int getIconHeight() {
      return HEIGHT;
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
      return FLATLAF_DARK;
    }

    @Override
    public String toString() {
      return label;
    }
  }
}
