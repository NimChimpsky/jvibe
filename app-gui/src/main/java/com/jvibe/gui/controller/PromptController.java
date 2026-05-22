package com.jvibe.gui.controller;

import com.jvibe.agent.AgentRunner;
import com.jvibe.events.AgentEvent;
import com.jvibe.session.AgentSession;
import jakarta.inject.Singleton;
import java.util.stream.Stream;

@Singleton
public final class PromptController {
  private final AgentRunner runner;

  public PromptController(AgentRunner runner) {
    this.runner = runner;
  }

  public Stream<AgentEvent> submit(AgentSession session, String prompt) {
    var text = prompt == null ? "" : prompt.trim();
    if (text.isEmpty()) {
      return Stream.empty();
    }
    return runner.run(session, text);
  }
}
