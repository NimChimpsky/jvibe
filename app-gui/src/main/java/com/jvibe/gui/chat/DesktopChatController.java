package com.jvibe.gui.chat;

import com.jvibe.config.ConfigService;
import com.jvibe.gui.controller.ModelSelectionController;
import com.jvibe.session.AgentSession;
import com.jvibe.session.SessionFactory;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/** Owns Chat/Build conversations and their per-view Swing state. */
public final class DesktopChatController implements AutoCloseable {
  public static final int COMPOSER_HEIGHT = ChatComposer.HEIGHT;
  public static final int PROMPT_INPUT_HEIGHT = ChatComposer.INPUT_HEIGHT;
  public static final int FILE_REFERENCE_ROW_HEIGHT = ChatComposer.REFERENCE_ROW_HEIGHT;

  private final ConfigService config;
  private final ModelSelectionController models;
  private final ChatPromptRunner promptRunner;
  private final SessionFactory sessions;
  private final Callbacks callbacks;
  private final JTabbedPane tabs = new JTabbedPane();
  private final Map<Path, TabState> workspaceTabs = new LinkedHashMap<>();
  private TabState active;
  private int chatSequence;
  private final boolean buildOnly;

  DesktopChatController(
      ConfigService config,
      ModelSelectionController models,
      ChatPromptRunner promptRunner,
      SessionFactory sessions,
      Callbacks callbacks,
      Path initialWorkspace,
      boolean buildOnly) {
    this.config = config;
    this.models = models;
    this.promptRunner = promptRunner;
    this.sessions = sessions;
    this.callbacks = callbacks;
    this.buildOnly = buildOnly;
    active = initialWorkspace != null ? createInitialWorkspace(initialWorkspace)
        : buildOnly ? null : createDefaultChat(false);
    if (active == null) showEmptyBuild();
    tabs.addChangeListener(event -> selectedTabChanged());
    tabs.setSelectedIndex(0);
  }

  private TabState createInitialWorkspace(Path root) {
    var normalized = root.toAbsolutePath().normalize();
    if (!java.nio.file.Files.exists(normalized.resolve(".git"))) {
      throw new IllegalArgumentException("Build requires a Git repository");
    }
    var state = new TabState(sessions.create(normalized), normalized, new ArrayList<>());
    workspaceTabs.put(normalized, state);
    addTab(fileName(normalized), normalized.toString(), state);
    return state;
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

  public void focusPrompt() {
    if (active != null) {
      SwingUtilities.invokeLater(() -> active.composer.prompt().requestFocusInWindow());
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
    active.composer.prompt().requestFocusInWindow();
  }

  public void createWorkspace(Path root) {
    if (!buildOnly) throw new IllegalStateException("Open projects in Build, not Chat");
    var normalized = root.toAbsolutePath().normalize();
    if (!java.nio.file.Files.exists(normalized.resolve(".git"))) {
      throw new IllegalArgumentException("Build requires a Git repository");
    }
    var existing = workspaceTabs.get(normalized);
    if (existing != null) {
      select(existing);
      return;
    }
    var state = new TabState(sessions.create(normalized), normalized, new ArrayList<>());
    workspaceTabs.put(normalized, state);
    addTab(fileName(normalized), normalized.toString(), state);
    select(state);
    callbacks.log("INFO", "Created workspace tab: " + normalized);
  }

  @Override
  public void close() {
    forEachState(state -> {
      if (state.runControl != null) state.runControl.cancel();
      if (state.terminal != null) state.terminal.close();
    });
  }

  private TabState createDefaultChat(boolean select) {
    var references = new ArrayList<Path>();
    var context = new ArrayList<Path>();
    var session = sessions.createDefaultChat(config.paths().tmp(), () -> List.copyOf(context));
    var state = new TabState(session, config.paths().tmp(), references);
    state.contextReferences = context;
    var number = ++chatSequence;
    addTab(
        number == 1 ? "Chat" : "Chat " + number,
        "Inline responses; no file editing",
        state);
    if (select) {
      select(state);
      callbacks.log("INFO", "Started new chat");
    }
    return state;
  }

  private void addTab(String title, String tooltip, TabState state) {
    if (buildOnly && active == null && workspaceTabs.size() <= 1) tabs.removeAll();
    var component = buildTab(state);
    component.putClientProperty("state", state);
    tabs.addTab(title, component);
    var index = tabs.getTabCount() - 1;
    tabs.setToolTipTextAt(index, tooltip);
    tabs.setTabComponentAt(index, tabTitle(title, tooltip, state));
  }

  private JComponent buildTab(TabState state) {
    state.transcript = new ChatTranscript();
    var chat = new JPanel(new BorderLayout());
    chat.add(scroll(state.transcript.component()), BorderLayout.CENTER);

    var chatAndComposer = new JPanel(new BorderLayout());
    chatAndComposer.add(chat, BorderLayout.CENTER);
    chatAndComposer.add(buildComposer(state), BorderLayout.SOUTH);

    if (!buildOnly) return chatAndComposer;
    state.terminal = new BuildTerminal(state.workingDirectory, chatAndComposer, message -> callbacks.log("INFO", message));
    return state.terminal.component();
  }

  private JComponent buildComposer(TabState state) {
    state.composer = new ChatComposer(
        () -> send(state),
        () -> applyModel(state, !state.refreshingModels));
    refreshModels(state);
    state.composer.onAttach(() -> {
      var chooser = new javax.swing.JFileChooser();
      chooser.setFileSelectionMode(javax.swing.JFileChooser.FILES_AND_DIRECTORIES);
      chooser.setMultiSelectionEnabled(true);
      if (chooser.showOpenDialog(tabs) == javax.swing.JFileChooser.APPROVE_OPTION) {
        for (var file : chooser.getSelectedFiles()) addFileReference(file.toPath());
      }
    });
    return state.composer;
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
    } else {
      active = null;
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
        if (state.terminal != null) state.terminal.close();
        if (state.runControl != null) state.runControl.cancel();
        workspaceTabs.values().removeIf(value -> value == state);
        tabs.removeTabAt(index);
        if (tabs.getTabCount() == 0) {
          active = null;
          if (buildOnly) showEmptyBuild(); else createDefaultChat(true);
        }
        callbacks.activeSessionChanged();
        return;
      }
    }
  }

  private void refreshModels(TabState state) {
    if (state.composer == null || state.running) return;
    var selector = state.composer.modelSelector();
    var current = models.display(state.session.provider(), state.session.model());
    state.refreshingModels = true;
    try {
      selector.removeAllItems();
      var containsCurrent = false;
      for (var choice : models.choices()) {
        selector.addItem(choice.display());
        containsCurrent |= choice.display().equals(current);
      }
      if (containsCurrent) {
        selector.setSelectedItem(current);
      } else if (selector.getItemCount() > 0) {
        selector.setSelectedIndex(0);
        applyModel(state, false);
      } else {
        state.session.selectModel("", "");
      }
    } finally {
      state.refreshingModels = false;
    }
  }

  private void applyModel(TabState state, boolean logChange) {
    var selected = state.composer.modelSelector().getSelectedItem();
    if (selected == null) return;
    var value = selected.toString();
    var previous = models.display(state.session.provider(), state.session.model());
    models.apply(state.session, value);
    if (logChange && !value.equals(previous)) callbacks.log("INFO", "Selected model: " + value);
  }

  private void send(TabState state) {
    if (state.running) {
      if (state.runControl != null) state.runControl.cancel();
      return;
    }
    var typed = state.composer.prompt().getText().trim();
    if (typed.isEmpty() && state.references.isEmpty()) return;
    if (state.composer.modelSelector().getSelectedItem() == null) {
      var message = "Select a JVibe model in the JVibe tab.";
      state.transcript.appendAssistant("Error: " + message);
      callbacks.log("ERROR", message);
      return;
    }
    var references = List.copyOf(state.references);
    references.stream().filter(path -> !state.contextReferences.contains(path)).forEach(state.contextReferences::add);
    var request = promptWithReferences(typed, references);
    state.composer.prompt().setText("");
    state.references.clear();
    refreshReferences(state);
    state.transcript.appendUser(typed, references);
    state.transcript.finishAssistant();
    state.running = true;
    state.composer.setSending(true);
    state.runControl = promptRunner.submit(state.session, state.transcript, request, () -> {
      state.running = false;
      state.runControl = null;
      state.composer.setSending(false);
      refreshModels(state);
    }, callbacks);
  }

  public void restartActiveShell() {
    if (buildOnly && active != null) active.terminal.restart();
  }

  public void toggleActiveShell() {
    if (buildOnly && active != null) active.terminal.toggle();
  }

  private void refreshReferences(TabState state) {
    state.composer.showReferences(state.references, path -> {
      state.references.remove(path);
      refreshReferences(state);
    });
  }

  private void showEmptyBuild() {
    var placeholder = new JPanel(new java.awt.GridBagLayout());
    placeholder.add(new JLabel("Add a Git project to start building"));
    tabs.addTab("Build", placeholder);
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

  public interface Callbacks {
    void log(String level, String message);
    default void workspaceChanged() {}
    default void activeSessionChanged() {}
  }

  private static final class TabState {
    private final AgentSession session;
    private final Path workingDirectory;
    private final List<Path> references;
    private List<Path> contextReferences = new ArrayList<>();
    private ChatTranscript transcript;
    private BuildTerminal terminal;
    private ChatComposer composer;
    private boolean refreshingModels;
    private boolean running;
    private com.jvibe.agent.RunControl runControl;

    private TabState(AgentSession session, Path workingDirectory, List<Path> references) {
      this.session = session;
      this.workingDirectory = workingDirectory;
      this.references = references;
    }
  }

}
