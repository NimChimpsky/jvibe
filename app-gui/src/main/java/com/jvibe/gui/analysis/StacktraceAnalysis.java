package com.jvibe.gui.analysis;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record StacktraceAnalysis(
    Path file,
    String rawText,
    List<ParsedThread> threads,
    List<ParsedException> exceptions,
    List<StacktraceFinding> findings,
    Map<String, Integer> stateCounts,
    boolean truncated) {}
