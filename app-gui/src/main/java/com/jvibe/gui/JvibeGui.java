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
import com.jvibe.deployment.DeploymentService;
import com.jvibe.events.AgentEvent;
import com.jvibe.gui.chat.ChatTranscript;
import com.jvibe.gui.controller.ModelSelectionController;
import com.jvibe.gui.controller.PromptController;
import com.jvibe.gui.controller.ProviderSettingsController;
import com.jvibe.gui.deploy.DeployAgentDialog;
import com.jvibe.gui.files.FileSystemTree;
import com.jvibe.gui.log.GuiLogPanel;
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
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
  private DeploymentService deploymentService;
  private FileLogService fileLogService;
  private SessionFactory sessionFactory;
  private Workspace workspace;
  private AgentSession session;
  private JFrame frame;
  private ChatTranscript transcript;
  private JSplitPane chatAndShell;
  private ShellPanel shellPanel;
  private JPanel content;
  private JComboBox<String> modelSelector;
  private JTextArea prompt;
  private JPanel fileReferencePanel;
  private final List<Path> promptFileReferences = new ArrayList<>();
  private GuiLogPanel logPanel;
  private DefaultListModel<String> approvalsModel;
  private DefaultListModel<String> toolsModel;
  private StringBuilder activeAssistantLog = new StringBuilder();
  private int activeAssistantChars;
  private boolean promptRunning;
  private AppTheme currentTheme = AppTheme.FLATLAF_DARK;

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      configureApplication();
      try {
        new JvibeGui().start();
      } catch (Exception e) {
        JOptionPane.showMessageDialog(null, e.getMessage(), "jvibe startup failed", JOptionPane.ERROR_MESSAGE);
      }
    });
  }

  private static void configureApplication() {
    System.setProperty("apple.awt.application.name", "jvibe");
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
    deploymentService = scope.get(DeploymentService.class);
    fileLogService = scope.get(FileLogService.class);
    sessionFactory = scope.get(SessionFactory.class);
    configService.bootstrap();
    currentTheme = AppTheme.fromConfig(configService.config().theme());
    applyRunningTheme(currentTheme);
    initialiseCore(Path.of(System.getProperty("user.dir")));

    frame = new JFrame("jvibe");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent event) {
        if (shellPanel != null) {
          shellPanel.stopSession();
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
    SwingUtilities.invokeLater(() -> prompt.requestFocusInWindow());
  }

  private void initialiseCore(Path root) throws Exception {
    workspace = new Workspace(root);
    session = sessionFactory.create(workspace);
    refreshModelSelector();
    refreshTools();
  }

  private JComponent buildRoot() {
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

  private JComponent buildSidebar() {
    var top = new JPanel();
    top.setLayout(new BorderLayout(8, 0));
    top.setBorder(new EmptyBorder(16, 16, 12, 16));
    top.setMinimumSize(new Dimension(260, 0));
    top.setPreferredSize(new Dimension(260, 66));
    top.add(button("Deploy Agent", event -> showDeployAgentDialog()), BorderLayout.WEST);
    top.add(button("New Chat", event -> {
      transcript.clear();
      appendAssistant("New chat started.");
      log("INFO", "Started new chat");
    }), BorderLayout.EAST);

    var files = new JPanel(new BorderLayout(0, 8));
    files.setBorder(new EmptyBorder(8, 16, 8, 16));
    files.add(section("Files"), BorderLayout.NORTH);
    files.add(scroll(FileSystemTree.create(workspace.root(), this::addFileReference)), BorderLayout.CENTER);

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

    var topAndFiles = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, files);
    topAndFiles.setResizeWeight(0.0);
    topAndFiles.setDividerSize(6);
    topAndFiles.setBorder(null);

    var sections = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topAndFiles, bottom);
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
    if (modelSelector == null || modelSelectionController == null) {
      return;
    }
    var current = session == null ? "" : session.provider() + "/" + session.model();
    modelSelector.removeAllItems();
    var containsCurrent = false;
    for (var choice : modelSelectionController.choices()) {
      var display = choice.display();
      modelSelector.addItem(display);
      containsCurrent = containsCurrent || display.equals(current);
    }
    if (containsCurrent) {
      modelSelector.setSelectedItem(current);
    }
    if (!containsCurrent && modelSelector.getItemCount() > 0) {
      modelSelector.setSelectedIndex(0);
      applySelectedModel();
    } else if (modelSelector.getItemCount() == 0 && session != null) {
      session.selectModel("", "");
    }
  }

  private void applySelectedModel() {
    if (session == null || modelSelector == null || modelSelector.getSelectedItem() == null) {
      return;
    }
    var selected = modelSelector.getSelectedItem().toString();
    modelSelectionController.apply(session, selected);
    log("INFO", "Selected model: " + selected);
  }

  JComponent buildComposer() {
    prompt = new JTextArea(4, 20);
    prompt.setLineWrap(true);
    prompt.setWrapStyleWord(true);
    prompt.setMargin(new Insets(10, 10, 10, 10));
    prompt.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sendPrompt");
    prompt.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "insert-break");
    prompt.getActionMap().put("sendPrompt", new javax.swing.AbstractAction() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent event) {
        sendPrompt();
      }
    });

    var send = button("Send");
    send.addActionListener(event -> sendPrompt());

    modelSelector = new JComboBox<>();
    modelSelector.addActionListener(event -> applySelectedModel());
    refreshModelSelector();

    var modelPanel = new JPanel();
    modelPanel.add(new JLabel("Model"));
    modelPanel.add(modelSelector);

    var footer = new JPanel(new BorderLayout(10, 0));
    footer.add(modelPanel, BorderLayout.WEST);
    footer.add(send, BorderLayout.EAST);

    var terminalRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
    terminalRow.add(shellToggleButton());

    var controls = new JPanel(new BorderLayout(0, 6));
    controls.add(footer, BorderLayout.NORTH);
    controls.add(terminalRow, BorderLayout.SOUTH);

    fileReferencePanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 1));
    fileReferencePanel.setOpaque(false);
    var fileReferenceRowSize = new Dimension(0, FILE_REFERENCE_ROW_HEIGHT);
    fileReferencePanel.setMinimumSize(fileReferenceRowSize);
    fileReferencePanel.setPreferredSize(fileReferenceRowSize);
    fileReferencePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, FILE_REFERENCE_ROW_HEIGHT));

    var promptStack = new JPanel(new BorderLayout(0, 4));
    promptStack.add(fileReferencePanel, BorderLayout.NORTH);
    promptStack.add(fixedPromptScroll(prompt), BorderLayout.CENTER);

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
    approvalsModel = new DefaultListModel<>();
    if (toolsModel == null) {
      toolsModel = new DefaultListModel<>();
    }
    logPanel = new GuiLogPanel();

    var refresh = button("Refresh");
    refresh.addActionListener(event -> {
      refreshTools();
      refreshFullLog();
      log("INFO", "Refreshed tools");
    });

    var header = new JPanel(new BorderLayout());
    header.add(sectionLabel("Tools"), BorderLayout.WEST);
    header.add(refresh, BorderLayout.EAST);

    var toolsPanel = new JPanel(new BorderLayout(0, 10));
    toolsPanel.add(header, BorderLayout.NORTH);
    toolsPanel.add(scroll(list(toolsModel)), BorderLayout.CENTER);

    var topTabs = new JTabbedPane();
    topTabs.addTab("Log", scroll(logPanel.logList()));
    topTabs.addTab("Tools", toolsPanel);
    topTabs.addTab("Full Log", scroll(logPanel.fullLogArea()));
    refreshFullLog();

    var top = new JPanel(new BorderLayout(0, 10));
    top.setBorder(new EmptyBorder(12, 12, 8, 12));
    top.setMinimumSize(new Dimension(360, 0));
    top.add(topTabs, BorderLayout.CENTER);

    var bottom = new JPanel(new BorderLayout());
    bottom.setBorder(new EmptyBorder(8, 12, 12, 12));
    bottom.setMinimumSize(new Dimension(360, 0));
    bottom.add(section("Approvals"), BorderLayout.NORTH);
    bottom.add(scroll(list(approvalsModel)), BorderLayout.CENTER);

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
    transcript = new ChatTranscript();
    shellPanel = new ShellPanel(workspace.root(), message -> log("INFO", message));
    shellPanel.setVisible(false);

    var chatAndComposer = new JPanel(new BorderLayout());
    chatAndComposer.add(scroll(transcript.component()), BorderLayout.CENTER);
    chatAndComposer.add(buildComposer(), BorderLayout.SOUTH);

    chatAndShell = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chatAndComposer, shellPanel);
    chatAndShell.setResizeWeight(1.0);
    chatAndShell.setDividerSize(0);
    chatAndShell.setBorder(null);
    setContent(chatAndShell);
  }

  private void toggleTerminal() {
    if (shellPanel != null && shellPanel.isVisible()) {
      minimizeTerminal();
    } else {
      showTerminal();
    }
  }

  private void showTerminal() {
    if (chatAndShell == null || shellPanel == null) {
      return;
    }
    shellPanel.setVisible(true);
    chatAndShell.setDividerSize(6);
    refresh(chatAndShell);
    SwingUtilities.invokeLater(() -> {
      var available = chatAndShell.getHeight();
      chatAndShell.setDividerLocation(Math.max(180, available - SHELL_HEIGHT));
      shellPanel.startOrRestore();
    });
  }

  private void minimizeTerminal() {
    if (chatAndShell == null || shellPanel == null) {
      return;
    }
    shellPanel.setVisible(false);
    chatAndShell.setDividerSize(0);
    chatAndShell.setDividerLocation(1.0);
    refresh(chatAndShell);
    log("INFO", "Shell minimized");
  }

  private void restartTerminal() {
    if (shellPanel == null) {
      return;
    }
    if (!shellPanel.isVisible()) {
      showTerminal();
    }
    shellPanel.restart();
    log("INFO", "Shell restart requested");
  }

  private void showSettingsDialog() {
    new ProviderSettingsDialog(frame, providerSettingsController, this::refreshModelSelector, message -> log("INFO", message)).show();
  }

  private void showGeneralSettingsDialog() {
    new GeneralSettingsDialog(frame, configService, message -> log("INFO", message)).show();
  }

  private void showDeployAgentDialog() {
    new DeployAgentDialog(
        frame,
        deploymentService,
        workspace.root(),
        session.provider(),
        session.model(),
        message -> log("INFO", message)).show();
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
          existing.requireApproval(),
          existing.maxToolIterations(),
          theme.name(),
          existing.autonomyMode(),
          existing.allowedHighRiskTools(),
          existing.gatewayEnabled(),
          existing.gatewayHost(),
          existing.gatewayPort(),
          existing.deploymentProfiles()));
    } catch (IOException e) {
      log("ERROR", "Unable to save theme: " + e.getMessage());
    }
  }

  private void sendPrompt() {
    if (promptRunning) {
      log("WARN", "Prompt already running");
      return;
    }
    var typedText = prompt.getText().trim();
    if (typedText.isEmpty() && promptFileReferences.isEmpty()) {
      return;
    }
    if (modelSelector.getSelectedItem() == null) {
      var message = "No provider model selected. Open Providers and enable a provider.";
      appendAssistant("Error: " + message);
      log("ERROR", message);
      return;
    }
    var references = List.copyOf(promptFileReferences);
    var text = promptTextWithReferences(typedText, references);
    prompt.setText("");
    clearFileReferences();
    appendUser(typedText, references);
    transcript.finishAssistant();
    activeAssistantLog = new StringBuilder();
    activeAssistantChars = 0;
    promptRunning = true;
    status("Running");
    fileLogService.prompt(session.provider(), session.model(), text);
    refreshFullLog();
    log("INFO", "Prompt submitted to " + session.provider() + "/" + session.model());

    var worker = new SwingWorker<Void, AgentEvent>() {
      @Override
      protected Void doInBackground() {
        try {
          promptController.submit(session, text).forEach(this::publish);
        } catch (RuntimeException e) {
          publish(AgentEvent.error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        return null;
      }

      @Override
      protected void process(java.util.List<AgentEvent> events) {
        for (var event : events) {
          handleEvent(event);
        }
      }

      @Override
      protected void done() {
        promptRunning = false;
        status("Ready");
        try {
          get();
        } catch (Exception e) {
          log("ERROR", rootMessage(e));
        }
      }
    };
    worker.execute();
  }

  private void handleEvent(AgentEvent event) {
    switch (event.type()) {
      case ASSISTANT_DELTA -> appendAssistantDelta(event.message());
      case TOOL_STARTED -> log("INFO", "Tool started: " + event.message());
      case TOOL_FINISHED -> log("INFO", "Tool finished: " + event.message());
      case APPROVAL_REQUIRED -> {
        approvalsModel.add(0, timestamp() + " " + event.message());
        log("WARN", "Approval required: " + event.message());
      }
      case ERROR -> {
        flushAssistantLog();
        appendAssistant("Error: " + event.message());
        log("ERROR", event.message());
      }
      case DONE -> {
        flushAssistantLog();
        transcript.finishAssistant();
        if (activeAssistantChars == 0) {
          log("WARN", "Run completed without assistant text");
        } else {
          log("INFO", "Run complete");
        }
      }
    }
  }

  private void refreshTools() {
    if (session == null) {
      return;
    }
    if (toolsModel == null) {
      toolsModel = new DefaultListModel<>();
    } else {
      toolsModel.clear();
    }
    session.toolRegistry().definitions().stream()
        .sorted(Comparator.comparing(ToolDefinition::id))
        .map(tool -> tool.id() + "  [" + tool.risk() + "]\n" + tool.description())
        .forEach(toolsModel::addElement);
  }

  private void appendUser(String text) {
    appendUser(text, List.of());
  }

  private void appendUser(String text, List<Path> references) {
    transcript.appendUser(text, references);
  }

  private void appendAssistant(String text) {
    transcript.appendAssistant(text);
  }

  private void appendAssistantDelta(String text) {
    if (!transcript.appendAssistantDelta(text)) {
      return;
    }
    activeAssistantLog.append(text);
    activeAssistantChars += text.length();
  }

  private void flushAssistantLog() {
    if (fileLogService != null && !activeAssistantLog.isEmpty()) {
      fileLogService.assistant(session.provider(), session.model(), activeAssistantLog.toString());
      activeAssistantLog.setLength(0);
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
    var button = terminalButton(event -> toggleTerminal());
    var menu = new JPopupMenu();
    var restart = menu.add("Restart Shell");
    restart.addActionListener(event -> restartTerminal());
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
    var normalized = path.toAbsolutePath().normalize();
    if (promptFileReferences.contains(normalized)) {
      prompt.requestFocusInWindow();
      return;
    }
    promptFileReferences.add(normalized);
    refreshFileReferences();
    prompt.requestFocusInWindow();
    log("INFO", "Added file reference: " + normalized);
  }

  private void clearFileReferences() {
    promptFileReferences.clear();
    refreshFileReferences();
  }

  private void removeFileReference(Path path) {
    promptFileReferences.remove(path);
    refreshFileReferences();
    prompt.requestFocusInWindow();
  }

  private void refreshFileReferences() {
    if (fileReferencePanel == null) {
      return;
    }
    fileReferencePanel.removeAll();
    for (var path : promptFileReferences) {
      fileReferencePanel.add(fileReferenceChip(path));
    }
    refresh(fileReferencePanel);
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
