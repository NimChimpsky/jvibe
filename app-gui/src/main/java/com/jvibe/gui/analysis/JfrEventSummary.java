package com.jvibe.gui.analysis;

import java.time.Duration;
import java.time.Instant;

public record JfrEventSummary(
    String eventType,
    String label,
    long count,
    Instant firstTime,
    Instant lastTime,
    Duration totalDuration) {}
