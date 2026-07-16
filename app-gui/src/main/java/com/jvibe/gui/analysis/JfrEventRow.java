package com.jvibe.gui.analysis;

import java.time.Duration;
import java.time.Instant;

public record JfrEventRow(String eventType, Instant startTime, Duration duration, String description) {}
