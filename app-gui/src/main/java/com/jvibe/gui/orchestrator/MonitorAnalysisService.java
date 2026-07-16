package com.jvibe.gui.orchestrator;

import com.jvibe.agent.AgentRunner;
import com.jvibe.events.AgentEvent;
import com.jvibe.monitor.MonitoredService;
import com.jvibe.session.SessionFactory;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import java.util.stream.Stream;

@Singleton
public final class MonitorAnalysisService {
  private final AgentRunner runner;
  private final SessionFactory sessionFactory;

  public MonitorAnalysisService(@External AgentRunner runner, @External SessionFactory sessionFactory) {
    this.runner = runner;
    this.sessionFactory = sessionFactory;
  }

  public Stream<AgentEvent> analyze(Path workspace, MonitoredService service) {
    var session = sessionFactory.create(workspace);
    return runner.run(session, prompt(service));
  }

  private static String prompt(MonitoredService service) {
    return """
        Analyse this monitored JVM service and return concise findings, likely risks, and next steps.

        Service: %s
        URL: %s
        Host: %s
        Last seen: %s
        Health: %s
        Snapshot: %s
        """.formatted(
            service.name(),
            service.serviceUrl(),
            service.host(),
            service.lastSeen(),
            service.health(),
            service.latestSnapshot());
  }
}
