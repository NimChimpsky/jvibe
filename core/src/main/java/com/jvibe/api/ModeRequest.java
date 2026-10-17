package com.jvibe.api;

import com.jvibe.session.Message;
import com.jvibe.session.Mode;
import java.util.List;

/** Everything the client sends for one model turn; prompts and tool schemas live on the server. */
public record ModeRequest(
    Mode mode,
    List<Message> messages,
    Thinking thinking,
    String instructions,
    String context) {
  public ModeRequest {
    messages = List.copyOf(messages);
    instructions = instructions == null ? "" : instructions;
    context = context == null ? "" : context;
  }
}
