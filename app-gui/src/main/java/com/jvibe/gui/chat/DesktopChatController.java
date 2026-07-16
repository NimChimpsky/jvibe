package com.jvibe.gui.chat;

import com.jvibe.config.ConfigService;
import com.jvibe.events.AgentEvent;
import com.jvibe.gui.controller.ModelSelectionController;
import com.jvibe.gui.controller.PromptController;
import com.jvibe.gui.terminal.ShellPanel;
import com.jvibe.logging.FileLogService;
import com.jvibe.session.AgentSession;
import com.jvibe.session.SessionFactory;
import com.jvibe.tool.ToolRegistry;
import com.jvibe.workspace.Workspace;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

/** Owns Desktop chat/workspace tabs and all per-chat Swing state. */
public final class DesktopChatController implements AutoCloseable {
  public static final int COMPOSER_HEIGHT = 220;
  public static final int PROMPT_INPUT_HEIGHT = 118;
  public static final int FILE_REFERENCE_ROW_HEIGHT = 26;
  private static final int SHELL_HEIGHT = 220;

  private final ConfigService config;
  private final ModelSelectionController models;
  private final PromptController prompts;
  private final FileLogService fileLog;
  private final SessionFactory sessions;
  private final Callbacks callbacks;
  private final JTabbedPane tabs = new JTabbedPane();
  private final Map<Path, TabState> workspaceTabs = new LinkedHashMap<>();
  private TabState active;
  private int chatSequence;

  DesktopChatController(
      ConfigService config,
      ModelSelectionController models,
      PromptController prompts,
      FileLogService fileLog,
      SessionFactory sessions,
      Callbacks callbacks) {
    this.config = config;
    this.models = models;
    this.prompts = prompts;
    this.fileLog = fileLog;
    this.sessions = sessions;
    this.callbacks = callbacks;
    active = createDefaultChat(false);
    tabs.addChangeListener(event -> selectedTabChanged());
    tabs.setSelectedIndex(0);
  }

  public JComponent component() {
    return tabs;
  }

  public AgentSession activeSession() {
    return active == null ? null : active.session;
  }

  public Path activeWorkingDirectory() {
    return active == null ? config.paths().tmp() : active.workingDirectory;
  }

  public Workspace activeWorkspace() {
    return active == null ? new Workspace(config.paths().tmp()) : active.session.workspace();
  }

  public ToolRegistry activeToolRegistry() {
    return active == null ? new ToolRegistry() : active.session.toolRegistry();
  }

  public void focusPrompt() {
    if (active != null) {
      SwingUtilities.invokeLater(() -> active.prompt.requestFocusInWindow());
    }
  }

  public void refreshModelSelectors() {
    forEachState(this::refreshModels);
  }

  public void addFileReference(Path path) {
    if (active == null) return;
    var normalized = path.toAbsolutePath().normalize();
    if (!active.references.contains(normalized)) {
      active.references.add(normalized);
      refreshReferences(active);
      callbacks.log("INFO", "Added file reference: " + normalized);
    }
    active.prompt.requestFocusInWindow();
  }

  public void createWorkspace(Path root) {
    var normalized = root.toAbsolutePath().normalize();
    var existing = workspaceTabs.get(normalized);
    if (existing != null) {
      select(existing);
      return;
    }
    var state = new TabState(sessions.create(normalized), normalized, new ArrayList<>());
    workspaceTabs.put(normalized, state);
    addTab(fileName(normalized), normalized.toString(), state, null);
    select(state);
    callbacks.log("INFO", "Created workspace tab: " + normalized);
  }

  @Override
  public void close() {
    forEachState(state -> state.shell.stopSession());
  }

  private TabState createDefaultChat(boolean select) {
    var references = new ArrayList<Path>();
    var session = sessions.createDefaultChat(config.paths().tmp(), () -> List.copyOf(references));
    var state = new TabState(session, config.paths().tmp(), references);
    var number = ++chatSequence;
    addTab(
        number == 1 ? "Chat" : "Chat " + number,
        "No workspace write access",
        state,
        "<html>Right click to add files to context. Write access is limited to JVibe tmp and files appear in File Output.<br>"
            + "Create a workspace for full in-place file editing.</html>");
    if (select) {
      select(state);
      callbacks.log("INFO", "Started new chat");
    }
    return state;
  }

  private void addTab(String title, String tooltip, TabState state, String note) {
    var component = buildTab(state, note);
    component.putClientProperty("state", state);
    tabs.addTab(title, component);
    var index = tabs.getTabCount() - 1;
    tabs.setToolTipTextAt(index, tooltip);
    tabs.setTabComponentAt(index, tabTitle(title, tooltip, state));
  }

  private JComponent buildTab(TabState state, String note) {
    state.transcript = new ChatTranscript();
    state.shell = new ShellPanel(state.workingDirectory, message -> callbacks.log("INFO", message));
    state.shell.setVisible(false);

    var chat = new JPanel(new BorderLayout());
    if (note != null && !note.isBlank()) {
      var label = new JLabel(note);
      label.setBorder(new EmptyBorder(8, 12, 4, 12));
      label.setForeground(UIManager.getColor("Label.disabledForeground"));
      chat.add(label, BorderLayout.NORTH);
    }
    chat.add(scroll(state.transcript.component()), BorderLayout.CENTER);

    var chatAndComposer = new JPanel(new BorderLayout());
    chatAndComposer.add(chat, BorderLayout.CENTER);
    chatAndComposer.add(buildComposer(state), BorderLayout.SOUTH);

    state.chatAndShell = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chatAndComposer, state.shell);
    state.chatAndShell.setResizeWeight(1.0);
    state.chatAndShell.setDividerSize(0);
    state.chatAndShell.setBorder(null);
    return state.chatAndShell;
  }

  private JComponent buildComposer(TabState state) {
    state.prompt = new JTextArea(4, 20);
    state.prompt.setLineWrap(true);
    state.prompt.setWrapStyleWord(true);
    state.prompt.setMargin(new Insets(10, 10, 10, 10));
    state.prompt.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sendPrompt");
    state.prompt.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "insert-break");
    state.prompt.getActionMap().put("sendPrompt", new javax.swing.AbstractAction() {
      @Override
      public void actionPerformed(java.awt.event.ActionEvent event) {
        send(state);
      }
    });

    var send = new JButton("Send");
    send.addActionListener(event -> send(state));
    state.send = send;
    state.modelSelector = new JComboBox<>();
    state.modelSelector.addActionListener(event -> applyModel(state, !state.refreshingModels));
    refreshModels(state);

    var modelPanel = new JPanel();
    modelPanel.add(new JLabel("Model"));
    modelPanel.add(state.modelSelector);
    var footer = new JPanel(new BorderLayout(10, 0));
    footer.add(modelPanel, BorderLayout.WEST);
    footer.add(send, BorderLayout.EAST);

    var terminalRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
    terminalRow.add(shellButton(state));
    var controls = new JPanel(new BorderLayout(0, 6));
    controls.add(footer, BorderLayout.NORTH);
    controls.add(terminalRow, BorderLayout.SOUTH);

    state.referencePanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 1));
    state.referencePanel.setOpaque(false);
    state.referencePanel.setPreferredSize(new Dimension(0, FILE_REFERENCE_ROW_HEIGHT));
    state.referencePanel.setMinimumSize(new Dimension(0, FILE_REFERENCE_ROW_HEIGHT));
    state.referencePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, FILE_REFERENCE_ROW_HEIGHT));

    var promptStack = new JPanel(new BorderLayout(0, 4));
    promptStack.add(state.referencePanel, BorderLayout.NORTH);
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

  private JComponent tabTitle(String title, String tooltip, TabState state) {
    var panel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
    panel.setOpaque(false);
    panel.setToolTipText(tooltip);
    var label = new JLabel(title);
    label.setToolTipText(tooltip);
    panel.add(label);
    if (state.session.mode() == AgentSession.Mode.CHAT) {
      panel.add(tabAction("+", "New chat", () -> createDefaultChat(true)));
    }
    var menu = new JPopupMenu();
    menu.add("Delete").addActionListener(event -> delete(state));
    panel.setComponentPopupMenu(menu);
    label.setComponentPopupMenu(menu);
    panel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        select(state);
      }
    });
    return panel;
  }

  private static JLabel tabAction(String text, String tooltip, Runnable action) {
    var label = new JLabel(text);
    label.setToolTipText(tooltip);
    label.setBorder(new EmptyBorder(0, 4, 0, 4));
    label.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        action.run();
        event.consume();
      }
    });
    return label;
  }

  private void selectedTabChanged() {
    var selected = tabs.getSelectedComponent();
    if (selected instanceof JComponent component && component.getClientProperty("state") instanceof TabState state) {
      active = state;
      refreshModels(state);
      callbacks.activeSessionChanged();
      focusPrompt();
    }
  }

  private void select(TabState state) {
    for (var index = 0; index < tabs.getTabCount(); index++) {
      if (tabs.getComponentAt(index) instanceof JComponent component
          && component.getClientProperty("state") == state) {
        tabs.setSelectedIndex(index);
        return;
      }
    }
  }

  private void delete(TabState state) {
    for (var index = 0; index < tabs.getTabCount(); index++) {
      if (tabs.getComponentAt(index) instanceof JComponent component
          && component.getClientProperty("state") == state) {
        state.shell.stopSession();
        workspaceTabs.values().removeIf(value -> value == state);
        tabs.removeTabAt(index);
        if (tabs.getTabCount() == 0) createDefaultChat(true);
        callbacks.activeSessionChanged();
        return;
      }
    }
  }

  private void refreshModels(TabState state) {
    if (state.modelSelector == null) return;
    var current = models.display(state.session.provider(), state.session.model());
    state.refreshingModels = true;
    try {
      state.modelSelector.removeAllItems();
      var containsCurrent = false;
      for (var choice : models.choices()) {
        state.modelSelector.addItem(choice.display());
        containsCurrent |= choice.display().equals(current);
      }
      if (containsCurrent) {
        state.modelSelector.setSelectedItem(current);
      } else if (state.modelSelector.getItemCount() > 0) {
        state.modelSelector.setSelectedIndex(0);
        applyModel(state, false);
      } else {
        state.session.selectModel("", "");
      }
    } finally {
      state.refreshingModels = false;
    }
  }

  private void applyModel(TabState state, boolean logChange) {
    var selected = state.modelSelector.getSelectedItem();
    if (selected == null) return;
    var value = selected.toString();
    var previous = models.display(state.session.provider(), state.session.model());
    models.apply(state.session, value);
    if (logChange && !value.equals(previous)) callbacks.log("INFO", "Selected model: " + value);
  }

  private void send(TabState state) {
    if (state.running) {
      callbacks.log("WARN", "Prompt already running");
      return;
    }
    var typed = state.prompt.getText().trim();
    if (typed.isEmpty() && state.references.isEmpty()) return;
    if (state.modelSelector.getSelectedItem() == null) {
      var message = "No provider model selected. Open Providers and enable a provider.";
      state.transcript.appendAssistant("Error: " + message);
      callbacks.log("ERROR", message);
      return;
    }
    var references = List.copyOf(state.references);
    var request = promptWithReferences(typed, references);
    state.prompt.setText("");
    state.references.clear();
    refreshReferences(state);
    state.transcript.appendUser(typed, references);
    state.transcript.finishAssistant();
    state.assistantLog.setLength(0);
    state.assistantChars = 0;
    state.running = true;
    state.send.setEnabled(false);
    fileLog.prompt(state.session.provider(), state.session.model(), request);
    callbacks.fullLogChanged();
    callbacks.log("INFO", "Prompt submitted to " + state.session.provider() + "/" + state.session.model());

    new SwingWorker<Void, AgentEvent>() {
      @Override
      protected Void doInBackground() {
        try {
          prompts.submit(state.session, request).forEach(this::publish);
        } catch (RuntimeException e) {
          publish(AgentEvent.error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        return null;
      }

      @Override
      protected void process(List<AgentEvent> events) {
        events.forEach(event -> handle(state, event));
      }

      @Override
      protected void done() {
        state.running = false;
        state.send.setEnabled(true);
        callbacks.outputChanged();
        try {
          get();
        } catch (Exception e) {
          callbacks.log("ERROR", rootMessage(e));
        }
      }
    }.execute();
  }

  private void handle(TabState state, AgentEvent event) {
    switch (event.type()) {
      case ASSISTANT_DELTA -> {
        if (state.transcript.appendAssistantDelta(event.message())) {
          state.assistantLog.append(event.message());
          state.assistantChars += event.message().length();
        }
      }
      case TOOL_STARTED -> callbacks.log("INFO", "Tool started: " + event.message());
      case TOOL_FINISHED -> {
        callbacks.log("INFO", "Tool finished: " + event.message());
        callbacks.outputChanged();
      }
      case ERROR -> {
        flushAssistantLog(state);
        state.transcript.appendAssistant("Error: " + event.message());
        callbacks.log("ERROR", event.message());
      }
      case DONE -> {
        flushAssistantLog(state);
        state.transcript.finishAssistant();
        callbacks.log(state.assistantChars == 0 ? "WARN" : "INFO",
            state.assistantChars == 0 ? "Run completed without assistant text" : "Run complete");
      }
    }
  }

  private void flushAssistantLog(TabState state) {
    if (!state.assistantLog.isEmpty()) {
      fileLog.assistant(state.session.provider(), state.session.model(), state.assistantLog.toString());
      state.assistantLog.setLength(0);
      callbacks.fullLogChanged();
    }
  }

  private JButton shellButton(TabState state) {
    var button = terminalButton(event -> toggleShell(state));
    var menu = new JPopupMenu();
    var restart = new JMenuItem("Restart Shell");
    restart.addActionListener(event -> {
      showShell(state);
      state.shell.restart();
      callbacks.log("INFO", "Shell restart requested");
    });
    menu.add(restart);
    button.setComponentPopupMenu(menu);
    return button;
  }

  private void toggleShell(TabState state) {
    if (state.shell.isVisible()) {
      state.shell.setVisible(false);
      state.chatAndShell.setDividerSize(0);
      state.chatAndShell.setDividerLocation(1.0);
      refresh(state.chatAndShell);
    } else {
      showShell(state);
    }
  }

  private void showShell(TabState state) {
    state.shell.setVisible(true);
    state.chatAndShell.setDividerSize(6);
    refresh(state.chatAndShell);
    SwingUtilities.invokeLater(() -> {
      state.chatAndShell.setDividerLocation(Math.max(180, state.chatAndShell.getHeight() - SHELL_HEIGHT));
      state.shell.startOrRestore();
    });
  }

  private void refreshReferences(TabState state) {
    state.referencePanel.removeAll();
    for (var path : state.references) {
      state.referencePanel.add(fileReferenceChip(path, () -> {
        state.references.remove(path);
        refreshReferences(state);
      }));
    }
    refresh(state.referencePanel);
  }

  public static JButton fileReferenceChip(Path path, Runnable remove) {
    var button = new JButton(fileName(path) + " x");
    button.setToolTipText(path.toString());
    button.setFocusable(false);
    button.setMargin(new Insets(2, 8, 2, 8));
    button.addActionListener(event -> remove.run());
    return button;
  }

  public static JButton terminalButton(java.awt.event.ActionListener listener) {
    var button = new JButton(new TerminalIcon());
    button.setText("");
    button.setToolTipText("Open terminal");
    button.setFocusable(false);
    button.setPreferredSize(new Dimension(32, 28));
    button.setMinimumSize(new Dimension(32, 28));
    button.addActionListener(listener);
    return button;
  }

  public static JComponent composerForTest() {
    var references = new JPanel();
    references.setPreferredSize(new Dimension(0, FILE_REFERENCE_ROW_HEIGHT));
    var prompt = new JTextArea();
    var stack = new JPanel(new BorderLayout(0, 4));
    stack.add(references, BorderLayout.NORTH);
    stack.add(fixedPromptScroll(prompt), BorderLayout.CENTER);
    var panel = new JPanel(new BorderLayout());
    panel.setPreferredSize(new Dimension(0, COMPOSER_HEIGHT));
    panel.setMinimumSize(new Dimension(0, COMPOSER_HEIGHT));
    panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, COMPOSER_HEIGHT));
    panel.add(stack);
    return panel;
  }

  private static JScrollPane fixedPromptScroll(Component component) {
    var scroll = scroll(component);
    var size = new Dimension(0, PROMPT_INPUT_HEIGHT);
    scroll.setMinimumSize(size);
    scroll.setPreferredSize(size);
    scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, PROMPT_INPUT_HEIGHT));
    return scroll;
  }

  private static JScrollPane scroll(Component component) {
    var scroll = new JScrollPane(component);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    return scroll;
  }

  private static String promptWithReferences(String text, List<Path> references) {
    if (references.isEmpty()) return text;
    var builder = new StringBuilder("File references:\n");
    references.forEach(path -> builder.append("- ").append(fileName(path)).append(": ").append(path).append('\n'));
    if (!text.isBlank()) builder.append('\n').append(text);
    return builder.toString();
  }

  private static String fileName(Path path) {
    return path.getFileName() == null ? path.toString() : path.getFileName().toString();
  }

  private void forEachState(java.util.function.Consumer<TabState> action) {
    for (var index = 0; index < tabs.getTabCount(); index++) {
      if (tabs.getComponentAt(index) instanceof JComponent component
          && component.getClientProperty("state") instanceof TabState state) {
        action.accept(state);
      }
    }
  }

  private static void refresh(JComponent component) {
    component.revalidate();
    component.repaint();
  }

  private static String rootMessage(Exception exception) {
    var cause = exception;
    while (cause.getCause() instanceof Exception nested) cause = nested;
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }

  public interface Callbacks {
    void log(String level, String message);
    void outputChanged();
    void fullLogChanged();
    void activeSessionChanged();
  }

  private static final class TabState {
    private final AgentSession session;
    private final Path workingDirectory;
    private final List<Path> references;
    private ChatTranscript transcript;
    private JSplitPane chatAndShell;
    private ShellPanel shell;
    private JComboBox<String> modelSelector;
    private boolean refreshingModels;
    private JTextArea prompt;
    private JPanel referencePanel;
    private JButton send;
    private final StringBuilder assistantLog = new StringBuilder();
    private int assistantChars;
    private boolean running;

    private TabState(AgentSession session, Path workingDirectory, List<Path> references) {
      this.session = session;
      this.workingDirectory = workingDirectory;
      this.references = references;
    }
  }

  private static final class TerminalIcon implements Icon {
    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
      var g = (Graphics2D) graphics.create();
      try {
        g.setColor(component.getForeground());
        g.drawRoundRect(x, y + 1, 15, 12, 3, 3);
        g.drawLine(x + 4, y + 5, x + 7, y + 7);
        g.drawLine(x + 7, y + 7, x + 4, y + 9);
        g.drawLine(x + 9, y + 10, x + 13, y + 10);
      } finally {
        g.dispose();
      }
    }

    @Override public int getIconWidth() { return 16; }
    @Override public int getIconHeight() { return 14; }
  }
}
