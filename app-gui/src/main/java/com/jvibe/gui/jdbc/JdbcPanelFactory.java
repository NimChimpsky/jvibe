package com.jvibe.gui.jdbc;

import com.jvibe.config.ConfigService;
import com.jvibe.gui.controller.ModelSelectionController;
import com.jvibe.jdbc.JdbcConnectionRepository;
import com.jvibe.jdbc.JdbcCredentialStore;
import com.jvibe.jdbc.JdbcDatabaseService;
import com.jvibe.session.SessionFactory;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JSplitPane;

@Singleton
public final class JdbcPanelFactory {
  private final JdbcConnectionRepository repository;
  private final JdbcCredentialStore credentials;
  private final JdbcDatabaseService database;
  private final SessionFactory sessions;
  private final JdbcSqlGenerationService sqlGeneration;
  private final ModelSelectionController modelSelection;
  private final ConfigService configService;

  public JdbcPanelFactory(
      @External JdbcConnectionRepository repository,
      @External JdbcCredentialStore credentials,
      @External JdbcDatabaseService database,
      @External SessionFactory sessions,
      JdbcSqlGenerationService sqlGeneration,
      ModelSelectionController modelSelection,
      @External ConfigService configService) {
    this.repository = repository;
    this.credentials = credentials;
    this.database = database;
    this.sessions = sessions;
    this.sqlGeneration = sqlGeneration;
    this.modelSelection = modelSelection;
    this.configService = configService;
  }

  public JComponent create(Consumer<String> log) {
    var workbench = new JdbcWorkbenchPanel(
        database, credentials, sessions, sqlGeneration, modelSelection, configService, log);
    var admin = new JdbcAdminPanel(database, credentials, log);
    var connections = new JdbcConnectionSidebar(repository, credentials, database, selected -> {
      workbench.setConnection(selected);
      admin.setConnection(selected);
    }, log);

    var workAndAdmin = split(workbench, admin, 0.70);
    return split(connections, workAndAdmin, 0.20);
  }

  private static JSplitPane split(JComponent left, JComponent right, double weight) {
    var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
    split.setResizeWeight(weight);
    split.setContinuousLayout(true);
    split.setDividerSize(6);
    split.setBorder(null);
    return split;
  }
}
