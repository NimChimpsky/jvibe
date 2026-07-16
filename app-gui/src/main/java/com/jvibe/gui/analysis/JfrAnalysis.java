package com.jvibe.gui.analysis;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record JfrAnalysis(
    Path file,
    Instant startTime,
    Instant endTime,
    Duration duration,
    long eventCount,
    int eventTypeCount,
    boolean eventsTruncated,
    boolean stacksTruncated,
    List<JfrEventSummary> eventSummaries,
    List<JfrEventRow> events,
    List<JfrHotFrame> hotMethods,
    List<JfrHotFrame> hotClasses,
    String stackTraceTree) {}
