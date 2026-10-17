package com.jvibe.process;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessRunnerTest {
  @TempDir Path temp;
  private final ProcessRunner runner = new ProcessRunner();

  @Test
  void drainsBothOutputPipesWhileWritingLargeInput() throws Exception {
    var result = runner.run(command("pipes"), temp, new byte[1_000_000], Duration.ofSeconds(10), new AtomicBoolean());
    assertEquals(ProcessResult.Status.EXITED, result.status());
    assertEquals(0, result.exitCode());
    assertEquals(1_000_000, result.stderr().length());
    assertTrue(result.stdout().endsWith("input=1000000"));
  }

  @Test
  void cancellationAndInterruptionKillSpawnedChild() throws Exception {
    assertStopsChild(false);
    assertStopsChild(true);
  }

  private void assertStopsChild(boolean interrupt) throws Exception {
    var marker = temp.resolve("child-" + interrupt + ".pid");
    var cancelled = new AtomicBoolean();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var task = executor.submit(() -> runner.run(command("parent", marker.toString()), temp,
          new byte[0], Duration.ofSeconds(10), cancelled));
      var limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (!Files.exists(marker) && System.nanoTime() < limit) Thread.sleep(20);
      assertTrue(Files.exists(marker), "Child did not start");
      long pid = Long.parseLong(Files.readString(marker));
      if (interrupt) task.cancel(true); else cancelled.set(true);
      if (!interrupt) assertEquals(ProcessResult.Status.CANCELLED, task.get(3, TimeUnit.SECONDS).status());
      limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false) && System.nanoTime() < limit) Thread.sleep(20);
      assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false), "Child survived cancellation");
    }
  }

  private static List<String> command(String... args) throws Exception {
    var executable = Path.of(System.getProperty("java.home"), "bin",
        System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
    var classes = Path.of(Fixture.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    var command = new java.util.ArrayList<>(List.of(executable.toString(), "-cp", classes.toString(), Fixture.class.getName()));
    command.addAll(List.of(args));
    return command;
  }

  public static final class Fixture {
    public static void main(String[] args) throws Exception {
      switch (args[0]) {
        case "pipes" -> {
          byte[] output = new byte[1_000_000];
          java.util.Arrays.fill(output, (byte) 'x');
          System.err.write(output);
          System.out.write(output);
          System.out.print("input=" + System.in.readAllBytes().length);
        }
        case "parent" -> {
          var child = new ProcessBuilder(command("child", args[1])).inheritIO().start();
          child.waitFor();
        }
        case "child" -> {
          Files.writeString(Path.of(args[1]), Long.toString(ProcessHandle.current().pid()));
          Thread.sleep(30_000);
        }
        default -> throw new IllegalArgumentException(args[0]);
      }
    }
  }
}
