package com.jvibe.tool;

import com.jvibe.session.Mode;

/** Asks the user before a state-changing tool runs. The GUI decides when to prompt. */
@FunctionalInterface
public interface ToolApprover {
  ToolApprover ALWAYS = (mode, call) -> true;

  boolean approve(Mode mode, ToolCall call);
}
