package com.jvibe.gui.jdbc;

import com.jvibe.events.AgentEvent;
import com.jvibe.gui.chat.AttachmentChatPanel;
import com.jvibe.gui.chat.ChatCallbacks;
import com.jvibe.gui.chat.ChatPromptRunner;
import com.jvibe.gui.layout.SplitPaneSizing;
import com.jvibe.jdbc.JdbcConnectionConfig;
import com.jvibe.jdbc.JdbcConnectionRepository;
import com.jvibe.jdbc.JdbcCredentialStore;
import com.jvibe.jdbc.JdbcDatabaseService;
import com.jvibe.jdbc.JdbcQueryResult;
import com.jvibe.session.SessionFactory;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JSplitPane;

/** Databases tab: saved JDBC connections, a conversation over attached ones, and a results table. */
@Singleton
public final class JdbcPanelFactory {
  private final JdbcConnectionRepository repository;
  private final JdbcCredentialStore credentials;
  private final JdbcDatabaseService database;
  private final SessionFactory sessions;
  private final ChatPromptRunner runner;

  public JdbcPanelFactory(
      @External JdbcConnectionRepository repository,
      @External JdbcCredentialStore credentials,
      @External JdbcDatabaseService database,
      @External SessionFactory sessions,
      ChatPromptRunner runner) {
    this.repository = repository;
    this.credentials = credentials;
    this.database = database;
    this.sessions = sessions;
    this.runner = runner;
  }

  public JComponent create(ChatCallbacks callbacks) {
    var results = new JdbcResultsPanel();
    var chatCallbacks = new ChatCallbacks() {
      @Override public void log(String level, String message) { callbacks.log(level, message); }
      @Override public void toolFinished(AgentEvent event) {
        if (event.data().get("display") instanceof JdbcQueryResult result) results.show(result);
      }
    };
    var chat = new AttachmentChatPanel<>(
        new AttachmentChatPanel.Attachables<>(
            "Type @ to attach a connection, then ask in plain English. Example: @orders top 10 customers by revenue this year",
            repository::connections,
            JdbcConnectionConfig::id,
            JdbcConnectionConfig::name,
            JdbcConnectionConfig::jdbcUrl),
        sessions::databases,
        this::unlock,
        runner,
        chatCallbacks);
    var sidebar = new JdbcConnectionSidebar(repository, credentials, database, chat::attach,
        message -> callbacks.log("INFO", message));

    var chatAndResults = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chat, results);
    configure(chatAndResults, 0.65);
    var root = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, chatAndResults);
    configure(root, 0.2);
    return root;
  }

  /** Prompts once per connection for its password; stored passwords stay in memory only. */
  private boolean unlock(List<JdbcConnectionConfig> attached) {
    for (var connection : attached) {
      var password = JdbcPasswordPrompter.password(null, connection, credentials);
      if (password == null) return false;
      Arrays.fill(password, '\0');
    }
    return true;
  }

  private static void configure(JSplitPane split, double weight) {
    split.setResizeWeight(weight);
    SplitPaneSizing.initialProportion(split, weight);
    split.setContinuousLayout(true);
    split.setDividerSize(6);
    split.setBorder(null);
  }
}
