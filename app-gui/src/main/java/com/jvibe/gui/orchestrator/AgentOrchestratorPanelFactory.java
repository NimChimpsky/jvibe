package com.jvibe.gui.orchestrator;

import com.jvibe.deployment.DeploymentService;
import com.jvibe.events.AgentEvent;
import com.jvibe.gui.deploy.DeployAgentDialog;
import com.jvibe.monitor.MonitorRegistry;
import com.jvibe.monitor.MonitoredService;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

@Singleton
public final class AgentOrchestratorPanelFactory {
  private final DeploymentService deploymentService;
  private final MonitorRegistry monitorRegistry;
  private final MonitorIntakeServer intakeServer;
  private final MonitorAnalysisService analysisService;

  public AgentOrchestratorPanelFactory(
      @External DeploymentService deploymentService,
      @External MonitorRegistry monitorRegistry,
      MonitorIntakeServer intakeServer,
      MonitorAnalysisService analysisService) {
    this.deploymentService = deploymentService;
    this.monitorRegistry = monitorRegistry;
    this.intakeServer = intakeServer;
    this.analysisService = analysisService;
  }

  public JComponent create(JFrame owner, Supplier<AgentOrchestratorContext> context, Consumer<String> log) {
    var tableModel = new DefaultTableModel(new Object[] {"Service", "URL", "Host", "Last seen", "Health"}, 0) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }
    };
    var table = new JTable(tableModel);
    table.setFillsViewportHeight(true);
    table.setAutoCreateRowSorter(true);
    table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    table.setRowHeight(24);
    var analysis = new JTextArea();
    analysis.setEditable(false);
    analysis.setLineWrap(true);
    analysis.setWrapStyleWord(true);

    var panel = new JPanel(new BorderLayout(0, 12));
    panel.setBorder(new EmptyBorder(18, 18, 18, 18));
    panel.add(header(owner, context, log, table, tableModel, analysis), BorderLayout.NORTH);
    panel.add(new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), new JScrollPane(analysis)), BorderLayout.CENTER);

    startIntake(log);
    refresh(tableModel);
    var refreshTimer = new javax.swing.Timer(5_000, event -> refresh(tableModel));
    panel.addHierarchyListener(event -> {
      if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
        if (panel.isDisplayable()) refreshTimer.start(); else refreshTimer.stop();
      }
    });
    return panel;
  }

  private JComponent header(
      JFrame owner,
      Supplier<AgentOrchestratorContext> context,
      Consumer<String> log,
      JTable table,
      DefaultTableModel tableModel,
      JTextArea analysis) {
    var header = new JPanel(new BorderLayout(0, 8));
    header.add(title("Agent Orchestrator"), BorderLayout.NORTH);
    header.add(description("Deploy autonomous agents and monitor JVM services. Monitoring is unauthenticated; use private/dev networks only."), BorderLayout.CENTER);

    var actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
    actions.add(button("Deploy Agent", event -> showDeployDialog(owner, context.get(), log)));
    actions.add(button("Add Service", event -> addService(owner, tableModel)));
    actions.add(button("Remove", event -> removeSelected(table, tableModel)));
    actions.add(button("Refresh", event -> refresh(tableModel)));
    actions.add(button("Analyze Selected", event -> analyzeSelected(table, analysis, context.get(), log)));
    header.add(actions, BorderLayout.SOUTH);
    return header;
  }

  private void startIntake(Consumer<String> log) {
    try {
      log.accept("Monitor intake listening at " + intakeServer.startIfEnabled());
    } catch (IOException e) {
      log.accept("Monitor intake failed: " + e.getMessage());
    }
  }

  private void addService(JFrame owner, DefaultTableModel tableModel) {
    var name = JOptionPane.showInputDialog(owner, "Service name");
    if (name == null || name.isBlank()) {
      return;
    }
    var url = JOptionPane.showInputDialog(owner, "Service URL", "http://127.0.0.1:8080");
    if (url == null) {
      return;
    }
    var host = JOptionPane.showInputDialog(owner, "Host/IP", "127.0.0.1");
    if (host == null) {
      return;
    }
    monitorRegistry.upsertManual(name, url, host);
    refresh(tableModel);
  }

  private void removeSelected(JTable table, DefaultTableModel tableModel) {
    var row = table.getSelectedRow();
    if (row < 0) {
      return;
    }
    var service = services().get(table.convertRowIndexToModel(row));
    monitorRegistry.remove(service.id());
    refresh(tableModel);
  }

  private void analyzeSelected(
      JTable table,
      JTextArea analysis,
      AgentOrchestratorContext context,
      Consumer<String> log) {
    var row = table.getSelectedRow();
    if (row < 0) {
      analysis.setText("Select a service first.");
      return;
    }
    var service = services().get(table.convertRowIndexToModel(row));
    analysis.setText("");
    new SwingWorker<Void, String>() {
      @Override
      protected Void doInBackground() {
        analysisService.analyze(context.workspace(), service).forEach(event -> {
          if (event.type() == AgentEvent.Type.ASSISTANT_DELTA) {
            publish(event.message());
          } else if (event.type() == AgentEvent.Type.ERROR) {
            publish("\nError: " + event.message());
          }
        });
        return null;
      }

      @Override
      protected void process(List<String> chunks) {
        chunks.forEach(analysis::append);
      }

      @Override
      protected void done() {
        log.accept("Monitor analysis complete for " + service.name());
      }
    }.execute();
  }

  private List<MonitoredService> services() {
    return monitorRegistry.services();
  }

  private void refresh(DefaultTableModel tableModel) {
    SwingUtilities.invokeLater(() -> {
      tableModel.setRowCount(0);
      for (var service : services()) {
        tableModel.addRow(new Object[] {
            service.name(),
            service.serviceUrl(),
            service.host(),
            service.lastSeen(),
            service.health()
        });
      }
    });
  }

  private void showDeployDialog(JFrame owner, AgentOrchestratorContext context, Consumer<String> log) {
    new DeployAgentDialog(
        owner,
        deploymentService,
        context.workspace(),
        context.provider(),
        context.model(),
        log).show();
  }

  private static JButton button(String text, java.awt.event.ActionListener listener) {
    var button = new JButton(text);
    button.addActionListener(listener);
    return button;
  }

  private static JLabel title(String text) {
    var label = new JLabel(text);
    label.setFont(label.getFont().deriveFont(Font.BOLD, 18));
    return label;
  }

  private static JLabel description(String text) {
    var label = new JLabel(text);
    label.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
    return label;
  }
}
