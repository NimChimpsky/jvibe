package com.jvibe.gui.controller;

import com.jvibe.agent.AgentRunner;
import com.jvibe.events.AgentEvent;
import com.jvibe.session.AgentSession;
import io.avaje.inject.External;
import jakarta.inject.Singleton;
import java.util.stream.Stream;

@Singleton
public final class PromptController {
  private final AgentRunner runner;

  public PromptController(@External AgentRunner runner) {
    this.runner = runner;
  }

  public Stream<AgentEvent> submit(AgentSession session, String prompt) {
    return submit(session, prompt, new com.jvibe.agent.RunControl());
  }

  public Stream<AgentEvent> submit(AgentSession session, String prompt, com.jvibe.agent.RunControl control) {
    var text = prompt == null ? "" : prompt.trim();
    if (text.isEmpty()) {
      return Stream.empty();
    }
    return runner.run(session, text, control);
  }
}
