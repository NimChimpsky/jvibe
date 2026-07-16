package com.jvibe.gui.analysis;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.StackTrace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JfrAnalysisServiceTest {
  @TempDir
  Path temp;

  @Test
  void analyzesRecordingMetadataAndEvents() throws Exception {
    assumeTrue(FlightRecorder.isAvailable());
    var file = temp.resolve("sample.jfr");
    try (var recording = new Recording()) {
      recording.enable(TestEvent.class).withStackTrace();
      recording.start();
      new TestEvent().commit();
      recording.stop();
      recording.dump(file);
    }

    var analysis = new JfrAnalysisService().analyze(file);

    assertTrue(analysis.eventCount() > 0);
    assertTrue(analysis.eventTypeCount() > 0);
    assertTrue(analysis.eventSummaries().stream().anyMatch(summary -> summary.eventType().equals("com.jvibe.TestEvent")));
    assertTrue(analysis.stackTraceTree().contains("Stack trace") || !analysis.hotMethods().isEmpty());
  }

  @Test
  void invalidFileThrowsReadableFailure() throws Exception {
    var file = temp.resolve("invalid.jfr");
    Files.writeString(file, "not a jfr");

    assertThrows(Exception.class, () -> new JfrAnalysisService().analyze(file));
  }

  @Name("com.jvibe.TestEvent")
  @StackTrace(true)
  static class TestEvent extends Event {}
}
