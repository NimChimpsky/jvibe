package com.jvibe.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Conversation {
  private final List<Message> messages = new ArrayList<>();

  public void add(Message message) {
    messages.add(message);
  }

  public List<Message> messages() {
    return Collections.unmodifiableList(messages);
  }
}
