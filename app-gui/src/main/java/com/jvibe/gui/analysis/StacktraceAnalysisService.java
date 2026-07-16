package com.jvibe.gui.analysis;

import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Singleton
public final class StacktraceAnalysisService {
  private static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
  private static final Pattern THREAD_HEADER = Pattern.compile("^\"([^\"]+)\".*");
  private static final Pattern EXCEPTION_HEADER = Pattern.compile("^(?:Caused by: )?([\\w.$]+(?:Exception|Error|Throwable))(?::\\s*(.*))?.*");

  public StacktraceAnalysis analyze(Path file) throws IOException {
    try (var input = Files.newInputStream(file)) {
      var bytes = input.readNBytes(MAX_FILE_BYTES + 1);
      var truncated = bytes.length > MAX_FILE_BYTES;
      var length = Math.min(bytes.length, MAX_FILE_BYTES);
      return analyze(file, new String(bytes, 0, length, java.nio.charset.StandardCharsets.UTF_8), truncated);
    }
  }

  public StacktraceAnalysis analyze(Path file, String text) {
    return analyze(file, text, false);
  }

  private StacktraceAnalysis analyze(Path file, String text, boolean truncated) {
    var lines = text.lines().toList();
    var threads = parseThreads(lines);
    var exceptions = parseExceptions(lines);
    var findings = findings(text, threads, exceptions);
    if (truncated) {
      findings.add(new StacktraceFinding("WARN", "Input was truncated at 16 MiB for desktop safety."));
    }
    return new StacktraceAnalysis(file, text, threads, exceptions, findings, stateCounts(threads), truncated);
  }

  private static List<ParsedThread> parseThreads(List<String> lines) {
    var threads = new ArrayList<ParsedThread>();
    String name = null;
    String state = "UNKNOWN";
    var frames = new ArrayList<String>();
    var locks = new ArrayList<String>();
    for (var line : lines) {
      var header = THREAD_HEADER.matcher(line);
      if (header.matches()) {
        if (name != null) {
          threads.add(new ParsedThread(name, state, List.copyOf(frames), List.copyOf(locks)));
        }
        name = header.group(1);
        state = "UNKNOWN";
        frames.clear();
        locks.clear();
      } else if (name != null) {
        var trimmed = line.trim();
        if (trimmed.startsWith("java.lang.Thread.State:")) {
          state = trimmed.substring("java.lang.Thread.State:".length()).trim().split(" ")[0];
        } else if (trimmed.startsWith("at ")) {
          frames.add(trimmed);
        } else if (trimmed.startsWith("-") || trimmed.contains("locked") || trimmed.contains("waiting to")) {
          locks.add(trimmed);
        } else if (trimmed.isBlank()) {
          threads.add(new ParsedThread(name, state, List.copyOf(frames), List.copyOf(locks)));
          name = null;
          state = "UNKNOWN";
          frames.clear();
          locks.clear();
        }
      }
    }
    if (name != null) {
      threads.add(new ParsedThread(name, state, List.copyOf(frames), List.copyOf(locks)));
    }
    return threads;
  }

  private static List<ParsedException> parseExceptions(List<String> lines) {
    var exceptions = new ArrayList<ParsedException>();
    String type = null;
    String message = "";
    var frames = new ArrayList<String>();
    for (var line : lines) {
      var matcher = EXCEPTION_HEADER.matcher(line.trim());
      if (matcher.matches()) {
        if (type != null) {
          exceptions.add(new ParsedException(type, message, List.copyOf(frames)));
        }
        type = matcher.group(1);
        message = matcher.group(2) == null ? "" : matcher.group(2);
        frames.clear();
      } else if (type != null) {
        var trimmed = line.trim();
        if (trimmed.startsWith("at ") || trimmed.startsWith("... ")) {
          frames.add(trimmed);
        } else if (trimmed.isBlank()) {
          exceptions.add(new ParsedException(type, message, List.copyOf(frames)));
          type = null;
          message = "";
          frames.clear();
        }
      }
    }
    if (type != null) {
      exceptions.add(new ParsedException(type, message, List.copyOf(frames)));
    }
    return exceptions;
  }

  private static List<StacktraceFinding> findings(
      String text,
      List<ParsedThread> threads,
      List<ParsedException> exceptions) {
    var findings = new ArrayList<StacktraceFinding>();
    var lower = text.toLowerCase(Locale.ROOT);
    if (lower.contains("deadlock")) {
      findings.add(new StacktraceFinding("ERROR", "Deadlock marker found in stacktrace text."));
    }
    if (lower.contains("outofmemoryerror") || lower.contains("java heap space")) {
      findings.add(new StacktraceFinding("ERROR", "OutOfMemoryError marker found."));
    }
    if (lower.contains("classnotfoundexception") || lower.contains("noclassdeffounderror")) {
      findings.add(new StacktraceFinding("WARN", "Class loading failure marker found."));
    }
    if (lower.contains("hs_err_pid") || lower.contains("sigsegv") || lower.contains("problematic frame")) {
      findings.add(new StacktraceFinding("ERROR", "Native JVM crash marker found."));
    }
    var blocked = threads.stream().filter(thread -> "BLOCKED".equals(thread.state())).count();
    if (blocked > 0) {
      findings.add(new StacktraceFinding("WARN", blocked + " blocked thread(s) found."));
    }
    var repeatedFrames = exceptions.stream().flatMap(exception -> exception.frames().stream()).filter(frame -> frame.startsWith("... ")).count();
    if (repeatedFrames > 0) {
      findings.add(new StacktraceFinding("INFO", repeatedFrames + " repeated-frame marker(s) found."));
    }
    return findings;
  }

  private static Map<String, Integer> stateCounts(List<ParsedThread> threads) {
    var counts = new LinkedHashMap<String, Integer>();
    for (var state : List.of("RUNNABLE", "BLOCKED", "WAITING", "TIMED_WAITING", "UNKNOWN")) {
      counts.put(state, 0);
    }
    for (var thread : threads) {
      counts.merge(thread.state(), 1, Integer::sum);
    }
    return counts;
  }
}
