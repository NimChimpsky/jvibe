package com.jvibe.session;

/** The four assistant tabs. Each maps to its own server endpoint, prompt and tool set. */
public enum Mode {
  CHAT("chat", "Chat"),
  BUILD("build", "Build"),
  SERVERS("servers", "Servers"),
  DATABASES("databases", "Databases");

  private final String id;
  private final String label;

  Mode(String id, String label) {
    this.id = id;
    this.label = label;
  }

  /** Stable lowercase id used in URLs and configuration. */
  public String id() {
    return id;
  }

  public String label() {
    return label;
  }

  /** Whether the mode has tools that change files, servers or data. */
  public boolean changesState() {
    return this != CHAT;
  }
}
