package com.jvibe.gui.chat;

import com.jvibe.events.AgentEvent;

/** Hooks a chat view exposes to its surroundings (logging, Git refresh, result tables). */
public interface ChatCallbacks {
  void log(String level, String message);

  default void toolFinished(AgentEvent event) {}

  default void activeSessionChanged() {}
}
