package com.jvibe.gui.chat;

import java.util.Map;

/** Compact, human-readable descriptions of tool calls for the transcript and approval dialog. */
public final class ToolCalls {
  private static final int MAX_INLINE = 160;

  private ToolCalls() {}

  /** For example {@code ssh_exec @web01  df -h} or {@code file_read  src/Main.java}. */
  public static String describe(String name, Map<String, Object> arguments) {
    var target = first(arguments, "serverId", "connectionId");
    var detail = first(arguments, "command", "sql", "path", "tablePattern", "message");
    var text = name + (target.isEmpty() ? "" : " @" + target) + (detail.isEmpty() ? "" : "  " + detail);
    var oneLine = text.replaceAll("\\s+", " ").trim();
    return oneLine.length() <= MAX_INLINE ? oneLine : oneLine.substring(0, MAX_INLINE - 1) + "…";
  }

  /** The full command or statement, for review before running it. */
  public static String detail(Map<String, Object> arguments) {
    return first(arguments, "command", "sql", "patch", "content", "message");
  }

  private static String first(Map<String, Object> arguments, String... keys) {
    for (var key : keys) {
      var value = arguments.get(key);
      if (value != null && !value.toString().isBlank()) return value.toString();
    }
    return "";
  }
}
