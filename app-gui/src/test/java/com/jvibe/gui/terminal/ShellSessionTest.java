package com.jvibe.gui.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.jediterm.core.util.TermSize;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShellSessionTest {
  @TempDir
  Path temp;

  @Test
  void writesInputCapturesOutputAndExits() throws Exception {
    assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
    var session = new ShellSession(temp, List.of("/bin/sh"));
    var output = new StringBuilder();
    var outputLatch = new CountDownLatch(1);
    var reader = reader(session, output, outputLatch, "hello");

    session.start();
    reader.start();
    session.write("printf 'hello\\n'\n");
    session.write("exit\n");

    assertTrue(outputLatch.await(3, TimeUnit.SECONDS));
    assertEquals(0, session.waitFor());
    session.close();
  }

  @Test
  void capturesPromptLikeOutputWithoutTrailingNewline() throws Exception {
    assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
    var session = new ShellSession(temp, List.of("/bin/sh"));
    var output = new StringBuilder();
    var outputLatch = new CountDownLatch(1);
    var reader = reader(session, output, outputLatch, "prompt>");

    session.start();
    reader.start();
    session.write("printf 'prompt>'\n");

    assertTrue(outputLatch.await(3, TimeUnit.SECONDS));
    session.close();
  }

  @Test
  void resizeUpdatesPtyWindowSize() throws Exception {
    assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
    var session = new ShellSession(temp, List.of("/bin/sh"));

    session.start();
    session.resize(new TermSize(100, 30));

    var size = session.windowSize();
    assertEquals(100, size.getColumns());
    assertEquals(30, size.getRows());
    session.close();
  }

  @Test
  void closeTerminatesRunningShell() throws Exception {
    assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
    var session = new ShellSession(temp, List.of("/bin/sh"));

    session.start();
    session.close();

    assertTrue(!session.isConnected());
  }

  private Thread reader(ShellSession session, StringBuilder output, CountDownLatch latch, String expected) {
    return Thread.ofVirtual().unstarted(() -> {
      var buffer = new char[256];
      try {
        int count;
        while ((count = session.read(buffer, 0, buffer.length)) >= 0) {
          synchronized (output) {
            output.append(buffer, 0, count);
            if (output.toString().contains(expected)) {
              latch.countDown();
            }
          }
        }
      } catch (Exception ignored) {
        // Session close terminates the blocking read during tests.
      }
    });
  }
}
