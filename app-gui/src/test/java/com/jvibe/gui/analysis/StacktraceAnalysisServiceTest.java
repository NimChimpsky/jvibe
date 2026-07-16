package com.jvibe.gui.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StacktraceAnalysisServiceTest {
  @Test
  void parsesExceptionStacktraceAndFindings() {
    var text = """
        java.lang.OutOfMemoryError: Java heap space
            at com.example.Work.run(Work.java:12)
            at com.example.Main.main(Main.java:5)
        Caused by: java.lang.ClassNotFoundException: com.example.Missing
            at java.base/java.lang.Class.forName(Class.java:1)
            ... 2 more
        """;

    var analysis = new StacktraceAnalysisService().analyze(java.nio.file.Path.of("stack.log"), text);

    assertEquals(2, analysis.exceptions().size());
    assertTrue(analysis.findings().stream().anyMatch(finding -> finding.message().contains("OutOfMemoryError")));
    assertTrue(analysis.findings().stream().anyMatch(finding -> finding.message().contains("Class loading")));
    assertTrue(analysis.findings().stream().anyMatch(finding -> finding.message().contains("repeated-frame")));
  }

  @Test
  void parsesThreadDumpStatesAndLocks() {
    var text = """
        Found one Java-level deadlock:

        "worker-1" #12 prio=5
           java.lang.Thread.State: BLOCKED (on object monitor)
            at com.example.Locked.run(Locked.java:10)
            - waiting to lock <0x1> (a java.lang.Object)

        "worker-2" #13 prio=5
           java.lang.Thread.State: RUNNABLE
            at com.example.Busy.run(Busy.java:20)
        """;

    var analysis = new StacktraceAnalysisService().analyze(java.nio.file.Path.of("threads.txt"), text);

    assertEquals(2, analysis.threads().size());
    assertEquals(1, analysis.stateCounts().get("BLOCKED"));
    assertEquals(1, analysis.stateCounts().get("RUNNABLE"));
    assertEquals(1, analysis.threads().get(0).lockLines().size());
    assertTrue(analysis.findings().stream().anyMatch(finding -> finding.message().contains("Deadlock")));
    assertTrue(analysis.findings().stream().anyMatch(finding -> finding.message().contains("blocked thread")));
  }
}
