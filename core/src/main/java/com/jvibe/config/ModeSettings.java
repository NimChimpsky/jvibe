package com.jvibe.config;

import io.avaje.jsonb.Json;

/** Per-tab user customisation. */
@Json
public record ModeSettings(String instructions, boolean askBeforeRunning) {
  public static final ModeSettings DEFAULT = new ModeSettings("", false);

  public ModeSettings {
    instructions = instructions == null ? "" : instructions;
  }
}
